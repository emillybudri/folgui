package com.example.folgui

import android.Manifest
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Carga { CARREGANDO, OK, ERRO }

/**
 * Coração do app: guarda os dados da sessão e liga cada tela às suas ações.
 *
 * Está dividido em sete partes, marcadas com comentários:
 * 1. dados do SQLite, 2. feriados, 3. Google Agenda, 4. conta,
 * 5. sono, eventos e pessoas, 6. assistente com IA, 7. telas.
 */
@Composable
fun FolguiApp(rotaPedida: StateFlow<String?>, onRotaAberta: () -> Unit) {
    val context = LocalContext.current

    val estadoFlow = remember { context.dataStore.data.map(::lerEstado) }
    val estado by estadoFlow.collectAsState(initial = null)

    // Abertura animada por um instante (não aparece quando o app é aberto pela notificação ou pelo widget)
    var abertura by rememberSaveable { mutableStateOf(rotaPedida.value == null) }
    LaunchedEffect(Unit) {
        delay(1400)
        abertura = false
    }
    AnimatedContent(
        targetState = !abertura && estado != null,
        transitionSpec = { fadeIn(tween(DURACAO_ABERTURA)) togetherWith fadeOut(tween(DURACAO_ABERTURA)) },
        label = "abertura"
    ) { pronto ->
        val carregado = estado
        if (pronto && carregado != null) {
            ConteudoApp(carregado, rotaPedida, onRotaAberta)
        } else {
            TelaAbertura()
        }
    }
}

private const val DURACAO_ABERTURA = 350
private const val DURACAO_BARRAS = 250

@Composable
private fun ConteudoApp(carregado: EstadoPerfil, rotaPedida: StateFlow<String?>, onRotaAberta: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val repository = remember { FolguiRepository(context) }
    val api = remember { ApiRepository() }
    val agendaRepository = remember { AgendaRepository(context) }
    val nuvem = remember { NuvemRepository() }
    val amigos = remember { AmigosRepository() }
    val servico = remember { ServicoAgenda(context, repository, agendaRepository) }

    // O login vem antes de tudo; sem o Firebase configurado, o app abre sem conta
    val destinoInicial = remember {
        val logado = !nuvem.configurada || nuvem.contaAtual != null
        if (carregado.perfil != null && logado) Rotas.INICIO else Rotas.BOAS_VINDAS
    }
    val perfil = carregado.perfil

    // ------------------------------------------------------------------
    // 1. Dados do SQLite (as mudanças na escala do usuário ficam na tabela de exceções com id -1)
    // ------------------------------------------------------------------
    val pessoas = remember { mutableStateListOf<Pessoa>() }
    val eventos = remember { mutableStateListOf<Evento>() }
    val grupos = remember { mutableStateListOf<Grupo>() }
    val excecoesVoce = remember { mutableStateMapOf<Int, Excecao>() }

    fun recarregar() {
        pessoas.clear()
        pessoas.addAll(repository.listarPessoas())
        eventos.clear()
        eventos.addAll(repository.listarEventos())
        grupos.clear()
        grupos.addAll(repository.listarGrupos())
        excecoesVoce.clear()
        excecoesVoce.putAll(repository.listarExcecoes()[Pessoa.ID_VOCE].orEmpty())
    }

    val voce = perfil?.comoPessoa(excecoesVoce.toMap()) ?: Pessoa(Pessoa.ID_VOCE, "Você", TipoEscala.SEMANAL, hoje())

    // A primeira leitura e, depois, cada evento marcado pelo relógio com o app aberto
    LaunchedEffect(Unit) { mudancasDoRelogio.collect { recarregar() } }

    val todas = listOf(voce) + pessoas

    /** Mensagem rápida; com [acao], mostra um botão (ex.: "Desfazer"). */
    fun avisar(mensagem: String, acao: String? = null, onAcao: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val resultado = snackbar.showSnackbar(
                message = mensagem,
                actionLabel = acao,
                duration = if (acao != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (resultado == SnackbarResult.ActionPerformed) onAcao()
        }
    }

    /** Registra (ou remove, com null) uma mudança na escala de alguém, com opção de desfazer. */
    fun mudarDia(pessoa: Pessoa, dia: Int, excecao: Excecao?) {
        val anterior = pessoa.excecoes[dia]
        fun aplicar(nova: Excecao?) {
            if (nova == null) repository.removerExcecao(pessoa.id, dia) else repository.salvarExcecao(pessoa.id, nova)
            recarregar()
        }
        aplicar(excecao)
        avisar(
            if (excecao == null) "${pessoa.nomeCurto} volta à escala normal em ${diaResumido(dia)}."
            else "${excecao.tipo.rotulo} registrado para ${pessoa.nomeCurto} em ${diaResumido(dia)}.",
            acao = "Desfazer"
        ) { aplicar(anterior) }
    }

    fun responderFeriado(dia: Int, trabalha: Boolean) = mudarDia(voce, dia, voce.respostaFeriado(dia, trabalha))

    // ------------------------------------------------------------------
    // 2. Feriados nacionais (BrasilAPI), carregados por ano conforme a necessidade
    // ------------------------------------------------------------------
    val feriados = remember { mutableStateMapOf<Int, String>() }
    val cargaFeriados = remember { mutableStateMapOf<Int, Carga>() }

    fun carregarFeriados(ano: Int) {
        if (cargaFeriados[ano] == Carga.OK || cargaFeriados[ano] == Carga.CARREGANDO) return
        cargaFeriados[ano] = Carga.CARREGANDO
        scope.launch {
            cargaFeriados[ano] = try {
                api.buscarFeriados(ano).forEach { feriados[it.dia] = it.nome }
                Carga.OK
            } catch (e: Exception) {
                Carga.ERRO
            }
        }
    }

    LaunchedEffect(Unit) {
        val ano = partes(hoje()).ano
        carregarFeriados(ano)
        carregarFeriados(ano + 1)
    }

    // ------------------------------------------------------------------
    // 3. Google Agenda (lida só depois que o usuário conecta e permite o acesso)
    // ------------------------------------------------------------------
    val agendaLida = remember { mutableStateMapOf<Int, List<CompromissoAgenda>>() }
    val agenda = semEventosDoFolgui(agendaLida, eventos)
    val agendaConectada = carregado.agendaConectada && agendaRepository.temPermissao()

    fun gravarAgendaConectada(conectada: Boolean) {
        scope.launch { context.dataStore.edit { it[CHAVE_AGENDA] = if (conectada) "1" else "0" } }
    }

    // Permissão de notificações (Android 13+), pedida no momento em que um lembrete é criado
    val pedirPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (!concedida) avisar("Sem permissão de notificações, os lembretes não serão exibidos.")
    }

    /**
     * Grava o evento na Google Agenda e guarda o id de lá, para mexer sempre no mesmo evento.
     * Com os lembretes gravados nela, quem avisa é a agenda e o alarme do Folgui sai.
     */
    fun copiarParaAgenda(evento: Evento): Boolean {
        val cor = grupoDoEvento(evento, repository.listarGrupos())?.cor
        val gravado = agendaRepository.salvar(evento, repository.agendaIdDe(evento.id), cor)
        val id = gravado.id ?: return false
        repository.salvarAgendaId(evento.id, id)
        repository.marcarAvisoNaAgenda(evento.id, gravado.avisosGravados)
        if (gravado.avisosGravados) AlarmScheduler.cancel(context, evento.id)
        return true
    }

    // Eventos à espera da resposta do acesso à agenda. O Android só aceita um pedido de permissão
    // por vez, então o de notificações espera este terminar.
    val aguardandoAgenda = remember { mutableListOf<Int>() }
    var pedirNotificacaoDepois by remember { mutableStateOf(false) }
    // O que continuar depois da resposta ao pedido de acesso (criar a lista da IA, por exemplo)
    var depoisDoAcesso by remember { mutableStateOf<(() -> Unit)?>(null) }

    val pedirAgenda = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val concedida = agendaRepository.temEscrita()
        gravarAgendaConectada(concedida)
        val aguardando = repository.listarEventos().filter { it.id in aguardandoAgenda }
        aguardandoAgenda.clear()
        when {
            !concedida -> avisar("Sem acesso à agenda. Você pode permitir nas configurações do Android.")
            aguardando.isEmpty() -> avisar("Google Agenda conectada.")
            aguardando.map(::copiarParaAgenda).all { it } -> avisar("Salvo na Google Agenda.")
            else -> avisar("Salvo só no Folgui: agenda indisponível.")
        }
        recarregar()
        depoisDoAcesso?.invoke()
        depoisDoAcesso = null
        if (pedirNotificacaoDepois) {
            pedirNotificacaoDepois = false
            pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Mantém a Google Agenda igual ao Folgui (na primeira vez, pede o acesso). Retorna se já gravou. */
    fun sincronizarAgenda(evento: Evento): Boolean {
        if (carregado.agendaDesligada) {
            // Fora da agenda, os avisos voltam a ser do Folgui
            if (evento.avisoNaAgenda) {
                repository.marcarAvisoNaAgenda(evento.id, false)
                AlarmScheduler.schedule(context, evento.copy(avisoNaAgenda = false))
            }
            return false
        }
        if (agendaRepository.temEscrita()) return copiarParaAgenda(evento)
        if (aguardandoAgenda.isEmpty()) pedirAgenda.launch(PERMISSOES_AGENDA)
        aguardandoAgenda.add(evento.id)
        return false
    }

    /** Chamar antes de excluir o evento do banco, enquanto o vínculo com a agenda ainda existe. */
    fun tirarDaAgenda(evento: Evento) {
        if (!carregado.agendaDesligada) agendaRepository.excluir(repository.agendaIdDe(evento.id))
    }

    fun conectarAgenda() {
        if (agendaRepository.temEscrita()) {
            gravarAgendaConectada(true)
            avisar("Google Agenda conectada.")
        } else {
            pedirAgenda.launch(PERMISSOES_AGENDA)
        }
    }

    fun desconectarAgenda() {
        gravarAgendaConectada(false)
        avisar("Google Agenda desconectada.")
    }

    // Navega só depois da gravação, para a próxima tela já mostrar os dados novos
    fun salvarPerfil(pessoa: Pessoa, depois: () -> Unit) {
        scope.launch {
            context.salvarPerfilNasPreferencias(pessoa)
            depois()
        }
    }

    // ------------------------------------------------------------------
    // 4. Conta: a cópia na nuvem só é enviada depois de comparada com o que já existe lá,
    // para um aparelho desatualizado não apagar o que foi feito em outro
    // ------------------------------------------------------------------
    var conta by remember { mutableStateOf(nuvem.contaAtual) }
    var nuvemPronta by remember { mutableStateOf(false) }
    var copiaEmConflito by remember { mutableStateOf<CopiaNuvem?>(null) }
    var ultimoEnviado by remember { mutableStateOf<String?>(null) }
    var entrando by remember { mutableStateOf(false) }
    var erroEntrada by remember { mutableStateOf<String?>(null) }
    var avisoEntrada by remember { mutableStateOf<String?>(null) }

    fun irParaInicio() {
        navController.navigate(Rotas.INICIO) {
            popUpTo(Rotas.BOAS_VINDAS) { inclusive = true }
        }
    }

    /** Depois do login, quem já tem escala segue para o início. */
    fun sairDoLogin() {
        if (navController.currentDestination?.route == Rotas.BOAS_VINDAS) irParaInicio()
    }

    /** Troca os dados do aparelho pelos da conta. */
    fun aplicarCopia(copia: CopiaNuvem, depois: () -> Unit) {
        val dados = textoParaDados(copia.dados)
        if (dados == null) {
            avisar("Não foi possível ler os dados da conta.")
            return
        }
        eventos.forEach { AlarmScheduler.cancel(context, it.id) }
        repository.substituirTudo(dados.pessoas, dados.excecoesVoce, dados.eventos, dados.grupos)
        dados.eventos.forEach { AlarmScheduler.schedule(context, it) }
        ultimoEnviado = copia.dados
        salvarPerfil(dados.perfil.comoPessoa(emptyMap())) {
            scope.launch {
                context.dataStore.edit { it[CHAVE_NUVEM_EM] = copia.atualizadoEm.toString() }
                recarregar()
                nuvemPronta = true
                depois()
            }
        }
    }

    /** Depois de qualquer login, o caminho é o mesmo: comparar com o que já está na conta. */
    fun entrarCom(login: suspend () -> ContaNuvem) {
        erroEntrada = null
        avisoEntrada = null
        entrando = true
        scope.launch {
            try {
                val nova = login()
                val copia = nuvem.baixar()
                conta = nova
                when {
                    copia == null -> {
                        nuvemPronta = true
                        if (perfil == null) navController.navigate(Rotas.CONFIGURAR) else sairDoLogin()
                    }
                    perfil == null -> aplicarCopia(copia) {
                        irParaInicio()
                        avisar("Seus dados voltaram.")
                    }
                    else -> copiaEmConflito = copia
                }
            } catch (e: GetCredentialCancellationException) {
                // A pessoa fechou a escolha de conta
            } catch (e: Exception) {
                erroEntrada = mensagemDeLogin(e)
                if (perfil != null) avisar(mensagemDeLogin(e))
            }
            entrando = false
        }
    }

    fun entrarComGoogle() = entrarCom { nuvem.entrar(context) }

    fun esqueciSenha(email: String) {
        scope.launch {
            try {
                nuvem.redefinirSenha(email)
                erroEntrada = null
                avisoEntrada = "Link enviado para $email"
            } catch (e: Exception) {
                erroEntrada = mensagemDeLogin(e)
            }
        }
    }

    fun sairDaConta() {
        scope.launch {
            nuvem.sair(context)
            context.dataStore.edit { it.remove(CHAVE_NUVEM_EM) }
            conta = null
            nuvemPronta = false
            ultimoEnviado = null
            navController.navigate(Rotas.BOAS_VINDAS) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // Ao abrir, traz o que foi alterado em outro aparelho. Sem internet, nada é enviado nesta sessão.
    LaunchedEffect(Unit) {
        if (conta == null) return@LaunchedEffect
        try {
            val copia = nuvem.baixar()
            val local = context.dataStore.data.first()[CHAVE_NUVEM_EM]?.toLongOrNull() ?: 0
            if (copia != null && copia.atualizadoEm > local) {
                aplicarCopia(copia) { if (perfil == null) irParaInicio() }
            } else {
                nuvemPronta = true
            }
        } catch (e: Exception) {
            nuvemPronta = false
        }
    }

    // Envia a cópia pouco depois de cada mudança (o tempo de espera junta mudanças seguidas)
    val copiaLocal = perfil?.let {
        dadosParaTexto(DadosNuvem(it, excecoesVoce.toMap(), pessoas.toList(), eventos.toList(), grupos.toList()))
    }
    LaunchedEffect(copiaLocal, conta, nuvemPronta, copiaEmConflito) {
        if (copiaLocal == null || conta == null || !nuvemPronta || copiaEmConflito != null) return@LaunchedEffect
        if (copiaLocal == ultimoEnviado) return@LaunchedEffect
        delay(2000)
        try {
            val momento = nuvem.enviar(copiaLocal)
            ultimoEnviado = copiaLocal
            context.dataStore.edit { it[CHAVE_NUVEM_EM] = momento.toString() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Tenta de novo na próxima mudança
        }
    }

    fun mudarModoFacil(ligado: Boolean) {
        scope.launch { context.dataStore.edit { it[CHAVE_MODO_FACIL] = if (ligado) "1" else "0" } }
    }

    // ------------------------------------------------------------------
    // 5. Sono, eventos e pessoas: salvar, remarcar e excluir
    // ------------------------------------------------------------------
    var editandoSono by rememberSaveable { mutableStateOf(false) }

    fun salvarSono(sono: Turno?) {
        val anterior = voce
        salvarPerfil(voce.copy(sono = sono)) {}
        avisar(
            sono?.let { "Sono das ${it.resumo} salvo." } ?: "Horário de sono removido.",
            acao = "Desfazer"
        ) { salvarPerfil(anterior) {} }
    }

    /** Salva a nova data e reagenda o lembrete, com opção de desfazer. */
    fun remarcarEvento(antes: Evento, depois: Evento) {
        fun gravar(e: Evento) {
            repository.atualizarEvento(e)
            AlarmScheduler.schedule(context, e)
            sincronizarAgenda(e)
            recarregar()
        }
        gravar(depois)
        avisar("Remarcado para ${diaResumido(depois.dia)} às ${depois.horario}.", acao = "Desfazer") { gravar(antes) }
    }

    /** Manda o evento para os amigos escolhidos, que aceitam ou recusam no app deles. */
    fun convidarAmigos(evento: Evento) {
        val convidados = amigosDoEvento(evento, pessoas)
        if (convidados.isEmpty() || conta == null) return
        scope.launch {
            try {
                convidados.forEach { amigos.enviarConvite(it, evento, voce.nome) }
                avisar("Convite enviado para ${convidados.joinToString(", ") { it.nome }}.")
            } catch (e: Exception) {
                avisar("Salvo, mas não consegui enviar o convite.")
            }
        }
    }

    /** O evento que sai de um grupo volta para a cor padrão da agenda. */
    fun devolverCorPadrao(antes: Evento?, depois: Evento) {
        if (antes != null && antes.grupoId != 0 && depois.grupoId == 0) {
            agendaRepository.aplicarCor(repository.agendaIdDe(depois.id), null)
        }
    }

    fun salvarEvento(evento: Evento, convidar: Boolean = true): Boolean {
        val id = if (evento.id == 0) {
            repository.inserirEvento(evento)
        } else {
            if (repository.atualizarEvento(evento)) evento.id else -1
        }
        if (id <= 0) {
            avisar("Não foi possível salvar o evento.")
            return false
        }
        val salvo = evento.copy(id = id)
        val agendado = AlarmScheduler.schedule(context, salvo)
        val naAgenda = sincronizarAgenda(salvo)
        devolverCorPadrao(eventos.firstOrNull { it.id == evento.id }, salvo)
        recarregar()
        if (evento.id == 0 && convidar) convidarAmigos(salvo)

        if (agendado && !NotificationHelper.temPermissao(context) && Build.VERSION.SDK_INT >= 33) {
            if (aguardandoAgenda.isEmpty()) {
                pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                pedirNotificacaoDepois = true
            }
        }
        val resultado = when {
            naAgenda -> "Salvo na Google Agenda."
            agendaRepository.temEscrita() && !carregado.agendaDesligada -> "Salvo só no Folgui: agenda indisponível."
            else -> "Evento salvo."
        }
        avisar(
            if (agendado) {
                val momento = salvo.proximosLembretes.first()
                "$resultado Lembrete em ${diaResumido(diaDoMomento(momento))} às ${horarioDoMomento(momento)}."
            } else {
                resultado
            }
        )
        return true
    }

    fun excluirEvento(evento: Evento) {
        AlarmScheduler.cancel(context, evento.id)
        tirarDaAgenda(evento)
        repository.excluirEvento(evento.id)
        navController.navigateUp()
        recarregar()
        avisar("Evento excluído.", acao = "Desfazer") {
            repository.inserirEvento(evento, manterId = true)
            AlarmScheduler.schedule(context, evento)
            sincronizarAgenda(evento)
            recarregar()
        }
    }

    fun salvarPessoa(pessoa: Pessoa, existente: Pessoa?) {
        val ok = if (existente == null) {
            repository.inserirPessoa(pessoa) > 0
        } else {
            repository.atualizarPessoa(pessoa.copy(id = existente.id))
        }
        recarregar()
        navController.navigateUp()
        avisar(
            when {
                !ok -> "Não foi possível salvar."
                existente == null -> "${pessoa.nome} entrou nas suas pessoas."
                else -> "Escala de ${pessoa.nome} atualizada."
            }
        )
    }

    fun excluirPessoa(pessoa: Pessoa) {
        repository.excluirPessoa(pessoa.id)
        navController.navigateUp()
        recarregar()
        avisar("${pessoa.nome} foi removida.", acao = "Desfazer") {
            repository.inserirPessoa(pessoa, manterId = true)
            pessoa.excecoes.values.forEach { repository.salvarExcecao(pessoa.id, it) }
            recarregar()
        }
    }

    val rotaExterna by rotaPedida.collectAsState()
    LaunchedEffect(rotaExterna) {
        val rota = rotaExterna ?: return@LaunchedEffect
        onRotaAberta()
        // Sem escala ou sem login, a pessoa continua no passo em que está
        if (perfil == null || (nuvem.configurada && conta == null)) return@LaunchedEffect
        if (rotaTemAbas(rota)) navController.irParaAba(rota) else navController.navigate(rota)
    }

    // Grupos: a cor é do grupo, então toda mudança aqui repinta os eventos dele na agenda
    var criandoGrupo by rememberSaveable { mutableStateOf(false) }

    /** Conta o que ficou sem pintar, para a pessoa saber. A próxima conciliação tenta de novo. */
    fun avisarPintura(falhas: List<Evento>, sucesso: String) {
        avisar(if (falhas.isEmpty()) sucesso else "${plural(falhas.size, "evento")} ficou sem a cor. Vou tentar de novo.")
    }

    fun criarGrupo(nome: String, cor: CorGrupo): Int {
        val id = repository.inserirGrupo(Grupo(nome = nome.trim(), cor = cor))
        recarregar()
        if (id <= 0) avisar("Não foi possível criar o grupo.")
        return id.coerceAtLeast(0)
    }

    fun editarGrupo(antes: Grupo, depois: Grupo) {
        repository.atualizarGrupo(depois)
        recarregar()
        if (antes.cor == depois.cor) {
            avisar("Grupo atualizado.")
            return
        }
        scope.launch {
            val falhas = servico.pintar(eventosDoGrupo(eventos, depois.id), depois.cor)
            avisarPintura(falhas, "Eventos do grupo agora são cor ${depois.cor.comum}.")
        }
    }

    fun excluirGrupo(grupo: Grupo, comEventos: Boolean) {
        val doGrupo = eventosDoGrupo(eventos, grupo.id)
        if (comEventos) {
            doGrupo.forEach {
                AlarmScheduler.cancel(context, it.id)
                tirarDaAgenda(it)
                repository.excluirEvento(it.id)
            }
        }
        repository.excluirGrupo(grupo.id)
        navController.navigateUp()
        recarregar()
        scope.launch {
            if (!comEventos) servico.pintar(doGrupo, null)
            avisar("Grupo excluído.", acao = "Desfazer".takeIf { !comEventos }) {
                repository.inserirGrupo(grupo, manterId = true)
                scope.launch {
                    servico.mover(doGrupo, grupo)
                    recarregar()
                }
            }
        }
    }

    fun moverEventos(lista: List<Evento>, grupo: Grupo?) {
        scope.launch {
            val falhas = servico.mover(lista, grupo)
            recarregar()
            avisarPintura(falhas, if (grupo != null) "Movido para ${grupo.nome}." else "Fora do grupo.")
        }
    }

    fun importarCompromisso(compromisso: CompromissoAgenda, grupo: Grupo) {
        scope.launch {
            val ok = servico.importar(compromisso, grupo)
            recarregar()
            avisar(if (ok) "Adicionado a ${grupo.nome}." else "Não consegui ler esse evento da agenda.")
        }
    }

    fun restaurarEvento(evento: Evento) {
        scope.launch {
            val ok = servico.restaurar(evento, grupoDoEvento(evento, grupos)?.cor)
            recarregar()
            avisar(if (ok) "De volta à Google Agenda." else "Não consegui gravar na agenda.")
        }
    }

    // ------------------------------------------------------------------
    // 6. Assistente com IA: a conversa fica na memória enquanto o app está aberto
    // ------------------------------------------------------------------
    val assistente = remember { AssistenteIA(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL) }
    val mensagensIA = remember { mutableStateListOf<MensagemIA>() }
    var pensando by remember { mutableStateOf(false) }

    // Lista de compromissos em revisão. Fica salva como texto para sobreviver a girar a tela
    var planoTexto by rememberSaveable { mutableStateOf<String?>(null) }
    val plano = remember(planoTexto) { planoTexto?.let(::textoParaPlano) }
    // Só importa com um plano aberto, e é uma consulta ao provedor: não pode rodar a cada recomposição
    val agendaGoogle by produceState(true, plano != null, agendaRepository.temEscrita()) {
        value = withContext(Dispatchers.IO) { agendaRepository.temAgendaGoogle() }
    }
    var criandoPlano by remember { mutableStateOf(false) }
    val compromissosPlano = remember { mutableStateListOf<CompromissoAgenda>() }
    val diasPlano = plano?.itens?.map { it.dia }?.toSet()

    fun mudarPlano(novo: PlanoLote?) {
        planoTexto = novo?.let(::planoParaTexto)
    }

    /** Lê a agenda no período exato da lista, que pode passar do que o app já carregou. */
    suspend fun lerCompromissos(p: PlanoLote): List<CompromissoAgenda> {
        val dias = p.itens.map { it.dia }
        if (dias.isEmpty() || !agendaRepository.temPermissao()) return emptyList()
        return try {
            agendaRepository.buscar(dias.min(), dias.max()).values.flatten()
        } catch (e: Exception) {
            emptyList()
        }
    }

    LaunchedEffect(diasPlano, agendaRepository.temPermissao()) {
        compromissosPlano.clear()
        plano?.let { compromissosPlano.addAll(lerCompromissos(it)) }
    }

    val analisesPlano = plano?.let { analisarPlano(it, eventos, compromissosPlano, voce) }.orEmpty()

    /** O grupo do plano: usa o que já existe, repintando se a cor mudou, ou cria um novo. */
    suspend fun grupoDoPlano(p: PlanoLote): Grupo? {
        if (!p.temGrupo) return null
        val cor = p.cor ?: corSugerida(grupos)
        val existente = grupoPorNome(repository.listarGrupos(), p.grupoNome)
        if (existente == null) {
            val id = repository.inserirGrupo(Grupo(nome = p.grupoNome, cor = cor))
            return if (id > 0) Grupo(id, p.grupoNome, cor) else null
        }
        if (existente.cor == cor) return existente
        val novo = existente.copy(cor = cor)
        repository.atualizarGrupo(novo)
        servico.pintar(eventosDoGrupo(repository.listarEventos(), novo.id), cor)
        return novo
    }

    /** Cria o que falta da lista. Com [soFolgui], a pessoa aceitou ficar sem a Google Agenda. */
    fun criarPlano(soFolgui: Boolean = false) {
        // Lido do estado, e não da composição: esta função pode rodar depois de um pedido de acesso
        val atual = planoTexto?.let(::textoParaPlano) ?: return
        if (criandoPlano) return
        if (!soFolgui && !agendaRepository.temEscrita() && depoisDoAcesso == null) {
            depoisDoAcesso = { criarPlano() }
            pedirAgenda.launch(PERMISSOES_AGENDA)
            return
        }
        val itens = itensParaCriar(atual, analisesPlano)
        val fora = atual.pendentes.size - itens.size
        if (itens.isEmpty()) {
            mudarPlano(null)
            mensagensIA.add(MensagemIA(deUsuario = false, texto = "Nada novo para criar: tudo já estava na sua agenda."))
            return
        }
        if (!soFolgui && carregado.agendaDesligada) gravarAgendaConectada(true)
        criandoPlano = true
        scope.launch {
            val grupo = grupoDoPlano(atual)
            val final = if (atual.temGrupo && grupo == null) {
                atual.copy(itens = atual.itens.map { it.copy(estado = EstadoItem.FALHOU, erro = "Não consegui criar o grupo") })
            } else {
                executarLote(
                    plano = atual.copy(itens = atual.itens.filter { i -> i.estado == EstadoItem.CRIADO || itens.any { it.n == i.n } }),
                    itens = itens,
                    grupoId = grupo?.id ?: 0,
                    criar = { servico.criar(it, grupo?.cor, soFolgui) },
                    onProgresso = ::mudarPlano
                )
            }
            mudarPlano(final)
            recarregar()
            criandoPlano = false
            val repetidos = if (fora > 0) "\n${plural(fora, "compromisso")} já existia e ficou de fora." else ""
            mensagensIA.add(MensagemIA(deUsuario = false, texto = resumoDoLote(final) + repetidos))
        }
    }

    /** O que a fala da pessoa faz com a lista; devolve a resposta que aparece na conversa. */
    suspend fun tratarAjuste(ajuste: AjusteLote, resposta: MensagemIA): MensagemIA {
        // A pessoa pode ter mexido na prévia enquanto a IA respondia
        val atual = planoTexto?.let(::textoParaPlano)
        if (ajuste.confirmar && !ajuste.mudaPlano && atual != null && planoPronto(atual)) {
            criarPlano()
            return resposta.copy(texto = "Criando os eventos…")
        }
        val novo = aplicarAjuste(atual, ajuste, grupos.toList())
        mudarPlano(novo)
        if (novo == null) return resposta.copy(texto = "Tudo bem, não criei nada.")

        // Primeira vez que a lista aparece: a agenda precisa estar liberada para conferir conflitos
        if (atual == null && !agendaRepository.temEscrita() && !carregado.agendaDesligada && depoisDoAcesso == null) {
            depoisDoAcesso = {}
            pedirAgenda.launch(PERMISSOES_AGENDA)
        }
        val analises = analisarPlano(novo, eventos.toList(), lerCompromissos(novo), voce)
        val faltando = ajuste.incompletos.takeIf { it > 0 }?.let {
            listOf("${plural(it, "compromisso")} ficou de fora por falta de data ou hora.")
        }.orEmpty()
        return resposta.copy(
            // Conta o que entrou de fato: o modelo às vezes reenvia a lista inteira
            texto = perguntaDoPlano(novo, grupos.toList(), novo.itens.size - (atual?.takeIf { !it.concluido }?.itens?.size ?: 0), analises),
            avisos = resposta.avisos + faltando
        )
    }

    /** Envia um pedido escrito ou gravado. A resposta passa pela conferência do app antes de aparecer. */
    fun pedirParaIA(pedido: String?, audio: File?, segundos: Int?) {
        val historico = mensagensIA.toList()
        val pessoasAgora = todas
        val eventosAgora = eventos.toList()
        val gruposAgora = grupos.toList()
        val contexto = contextoParaIA(pessoasAgora, eventosAgora, agenda.toMap(), feriados.toMap(), gruposAgora, plano)
        val indice = mensagensIA.size
        mensagensIA.add(MensagemIA(deUsuario = true, texto = pedido ?: "Transcrevendo…", audioSegundos = segundos))
        pensando = true
        scope.launch {
            val resposta = try {
                val base64 = audio?.let { arquivo ->
                    withContext(Dispatchers.IO) { Base64.encodeToString(arquivo.readBytes(), Base64.NO_WRAP) }
                }
                val aberto = planoTexto?.let(::textoParaPlano)?.concluido == false
                val r = semLoteDeUmSo(assistente.conversar(contexto, historico, pessoasAgora, eventosAgora, pedido, base64), aberto, gruposAgora)
                // O texto do áudio fica visível: é a primeira forma de conferir se a IA ouviu certo
                if (audio != null) {
                    mensagensIA[indice] = mensagensIA[indice].copy(texto = r.transcricao.ifBlank { "Áudio sem transcrição" })
                }
                val ajuste = r.ajuste
                if (ajuste != null) {
                    tratarAjuste(ajuste, r.mensagem)
                } else {
                    val (validas, avisos) = verificarAcoes(r.mensagem.acoes, pessoasAgora, eventosAgora, gruposAgora)
                    r.mensagem.copy(
                        acoes = validas,
                        avisos = r.mensagem.avisos + avisos,
                        descricoes = validas.map { descreverAcao(it, pessoasAgora, eventosAgora, gruposAgora) }
                    )
                }
            } catch (e: ErroIA) {
                MensagemIA(deUsuario = false, texto = e.message ?: "Algo deu errado.")
            } catch (e: Exception) {
                MensagemIA(deUsuario = false, texto = "Sem conexão com a IA. Verifique a internet e tente de novo.")
            } finally {
                audio?.delete()
            }
            // Se a IA falhou, o balão não fica preso em "Transcrevendo…"
            if (audio != null && mensagensIA[indice].texto == "Transcrevendo…") {
                mensagensIA[indice] = mensagensIA[indice].copy(texto = "Áudio enviado")
            }
            mensagensIA.add(resposta)
            pensando = false
        }
    }

    /** Aplica as mudanças sugeridas e devolve a função que desfaz tudo. */
    fun aplicarAcoes(acoes: List<AcaoIA>): () -> Unit {
        val desfazer = mutableListOf<() -> Unit>()
        var voceNovo = voce
        val alteradas = mutableMapOf<Int, Pessoa>()
        fun atual(id: Int): Pessoa? = if (id == Pessoa.ID_VOCE) voceNovo else alteradas[id] ?: pessoas.firstOrNull { it.id == id }
        fun atualizar(p: Pessoa) {
            if (p.ehVoce) voceNovo = p else alteradas[p.id] = p
        }

        acoes.forEach { a ->
            val p = atual(a.pessoaId) ?: return@forEach
            when (a.tipo) {
                TipoAcaoIA.FERIAS -> atualizar(p.copy(feriasInicio = a.dia, feriasFim = a.fimDia))
                TipoAcaoIA.REMOVER_FERIAS -> atualizar(p.copy(feriasInicio = null, feriasFim = null))
                TipoAcaoIA.SONO -> voceNovo = voceNovo.copy(sono = a.turno)
                TipoAcaoIA.CRIAR_EVENTO -> {
                    val novo = eventoResultante(a, eventos) ?: return@forEach
                    val id = repository.inserirEvento(novo)
                    if (id > 0) {
                        val criado = novo.copy(id = id)
                        AlarmScheduler.schedule(context, criado)
                        sincronizarAgenda(criado)
                        convidarAmigos(criado)
                        desfazer.add {
                            AlarmScheduler.cancel(context, id)
                            tirarDaAgenda(criado)
                            repository.excluirEvento(id)
                        }
                    }
                }
                TipoAcaoIA.EDITAR_EVENTO -> {
                    val antes = eventos.firstOrNull { it.id == a.eventoId } ?: return@forEach
                    val depois = eventoResultante(a, eventos) ?: return@forEach
                    repository.atualizarEvento(depois)
                    AlarmScheduler.schedule(context, depois)
                    sincronizarAgenda(depois)
                    devolverCorPadrao(antes, depois)
                    desfazer.add {
                        repository.atualizarEvento(antes)
                        AlarmScheduler.schedule(context, antes)
                        sincronizarAgenda(antes)
                    }
                }
                TipoAcaoIA.CRIAR_GRUPO -> {
                    val id = repository.inserirGrupo(Grupo(nome = a.titulo.orEmpty(), cor = a.cor ?: corSugerida(grupos)))
                    if (id > 0) desfazer.add { repository.excluirGrupo(id) }
                }
                TipoAcaoIA.EDITAR_GRUPO -> {
                    val antes = grupos.firstOrNull { it.id == a.grupoId } ?: return@forEach
                    val depois = antes.copy(nome = a.titulo ?: antes.nome, cor = a.cor ?: antes.cor)
                    val doGrupo = eventosDoGrupo(eventos, antes.id)
                    repository.atualizarGrupo(depois)
                    if (antes.cor != depois.cor) scope.launch { servico.pintar(doGrupo, depois.cor) }
                    desfazer.add {
                        repository.atualizarGrupo(antes)
                        if (antes.cor != depois.cor) scope.launch { servico.pintar(doGrupo, antes.cor) }
                    }
                }
                TipoAcaoIA.EXCLUIR_GRUPO -> {
                    val antes = grupos.firstOrNull { it.id == a.grupoId } ?: return@forEach
                    val doGrupo = eventosDoGrupo(eventos, antes.id)
                    repository.excluirGrupo(antes.id)
                    scope.launch { servico.pintar(doGrupo, null) }
                    desfazer.add {
                        repository.inserirGrupo(antes, manterId = true)
                        repository.moverParaGrupo(doGrupo.map { it.id }, antes.id)
                        scope.launch { servico.pintar(doGrupo, antes.cor) }
                    }
                }
                TipoAcaoIA.MOVER_EVENTOS -> {
                    val lista = eventos.filter { it.id in a.eventoIds.orEmpty() }
                    val grupo = grupos.firstOrNull { it.id == a.grupoId }
                    repository.moverParaGrupo(lista.map { it.id }, grupo?.id ?: 0)
                    scope.launch { servico.pintar(lista, grupo?.cor) }
                    desfazer.add {
                        lista.groupBy { it.grupoId }.forEach { (id, parte) ->
                            repository.moverParaGrupo(parte.map { it.id }, id)
                            scope.launch { servico.pintar(parte, grupos.firstOrNull { it.id == id }?.cor) }
                        }
                    }
                }
                TipoAcaoIA.AVISOS_EVENTOS -> {
                    val lista = eventos.filter { it.id in a.eventoIds.orEmpty() }
                    fun gravar(e: Evento) {
                        repository.atualizarEvento(e)
                        AlarmScheduler.schedule(context, e)
                        sincronizarAgenda(e)
                    }
                    lista.forEach { gravar(it.comAvisos(a.avisos.orEmpty())) }
                    desfazer.add { lista.forEach(::gravar) }
                }
                TipoAcaoIA.EXCLUIR_EVENTO -> {
                    val antes = eventos.firstOrNull { it.id == a.eventoId } ?: return@forEach
                    AlarmScheduler.cancel(context, antes.id)
                    tirarDaAgenda(antes)
                    repository.excluirEvento(antes.id)
                    desfazer.add {
                        repository.inserirEvento(antes, manterId = true)
                        AlarmScheduler.schedule(context, antes)
                        sincronizarAgenda(antes)
                    }
                }
                TipoAcaoIA.TURNO -> {
                    val principal = p.turnos.firstOrNull()
                    if (principal != null) atualizar(p.copy(padrao = p.padrao.map { if (it == principal) a.turno else it }))
                }
                else -> {
                    val dia = a.dia ?: return@forEach
                    val anterior = p.excecoes[dia]
                    val tipo = when (a.tipo) {
                        TipoAcaoIA.FOLGA -> TipoExcecao.FOLGA
                        TipoAcaoIA.AUSENCIA -> TipoExcecao.AUSENCIA
                        TipoAcaoIA.TRABALHO -> TipoExcecao.TRABALHO
                        else -> TipoExcecao.HORARIO
                    }
                    if (a.tipo == TipoAcaoIA.REMOVER_MUDANCA) {
                        repository.removerExcecao(p.id, dia)
                    } else {
                        repository.salvarExcecao(p.id, Excecao(dia, tipo, a.turno.takeIf { tipo.trabalha }))
                    }
                    desfazer.add {
                        if (anterior == null) repository.removerExcecao(p.id, dia) else repository.salvarExcecao(p.id, anterior)
                    }
                }
            }
        }

        alteradas.forEach { (id, nova) ->
            val antiga = pessoas.firstOrNull { it.id == id } ?: return@forEach
            repository.atualizarPessoa(nova)
            desfazer.add { repository.atualizarPessoa(antiga) }
        }
        if (voceNovo != voce) {
            val antigo = voce
            salvarPerfil(voceNovo) {}
            desfazer.add { salvarPerfil(antigo) {} }
        }
        recarregar()
        return {
            desfazer.reversed().forEach { it() }
            recarregar()
        }
    }

    /** Confirma as mudanças de uma resposta da IA, com opção de desfazer. */
    fun aplicarMensagemIA(indice: Int) {
        val mensagem = mensagensIA[indice]
        val desfazer = aplicarAcoes(mensagem.acoes)
        mensagensIA[indice] = mensagem.copy(situacao = SituacaoAcoes.APLICADAS)
        avisar("Mudanças aplicadas.", acao = "Desfazer") {
            desfazer()
            mensagensIA[indice] = mensagem.copy(situacao = SituacaoAcoes.PENDENTE)
        }
    }

    // ------------------------------------------------------------------
    // 7. Telas: a barra de cima, as abas e a lista de rotas
    // ------------------------------------------------------------------
    val entrada by navController.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route
    val mostrarAbas = rotaTemAbas(rotaAtual)
    // Enquanto a barra sai, a aba de onde a pessoa veio continua marcada
    var ultimaAba by remember { mutableStateOf(Rotas.INICIO) }
    if (mostrarAbas && rotaAtual != null) ultimaAba = rotaAtual

    // Quem mexe no Google Calendar volta para cá sem trocar de tela: a volta também conta
    var voltasAoApp by remember { mutableIntStateOf(0) }
    val ciclo = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) voltasAoApp++
        }
        ciclo.addObserver(observador)
        onDispose { ciclo.removeObserver(observador) }
    }

    // Recarrega a agenda ao trocar de tela: um evento enviado para a Google Agenda logo aparece aqui
    LaunchedEffect(agendaConectada, rotaAtual, voltasAoApp) {
        if (agendaConectada) {
            val dados = try {
                agendaRepository.buscar(hoje() - 62, hoje() + 400)
            } catch (e: Exception) {
                emptyMap()
            }
            agendaLida.clear()
            agendaLida.putAll(dados)
            val vinculos = vinculosAgenda(repository.listarEventos(), dados)
            if (vinculos.isNotEmpty()) {
                vinculos.forEach { (id, agendaId) -> repository.salvarAgendaId(id, agendaId) }
                recarregar()
            }
            // Traz o que mudou direto na agenda: horário, nome, lembretes e eventos apagados
            if (servico.conciliar()) recarregar()
        } else {
            agendaLida.clear()
        }
    }

    // Amigos conectados: publica a sua escala, traz a deles e busca os convites
    var codigoAmigo by remember { mutableStateOf<String?>(null) }
    val convites = remember { mutableStateListOf<Convite>() }
    var adicionandoAmigo by rememberSaveable { mutableStateOf(false) }
    var conectandoAmigo by remember { mutableStateOf(false) }
    var erroAmigo by remember { mutableStateOf<String?>(null) }
    var amigosFalhou by remember { mutableStateOf(false) }

    suspend fun sincronizarAmigos() {
        if (conta == null || perfil == null) return
        val situacao = amigos.sincronizar(voce, repository)
        codigoAmigo = situacao.codigo
        convites.clear()
        convites.addAll(situacao.convites)
        if (situacao.mudou) recarregar()
    }

    LaunchedEffect(conta, rotaAtual, voce) {
        try {
            sincronizarAmigos()
            amigosFalhou = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sem internet: as escalas guardadas no aparelho continuam valendo
            amigosFalhou = true
        }
    }

    fun conectarAmigo(codigo: String) {
        conectandoAmigo = true
        erroAmigo = null
        scope.launch {
            try {
                amigos.adicionar(codigo)
                sincronizarAmigos()
                adicionandoAmigo = false
                avisar("Amigo conectado!")
            } catch (e: ErroAmigo) {
                erroAmigo = e.message
            } catch (e: Exception) {
                erroAmigo = "Não consegui conectar. Veja a internet."
            }
            conectandoAmigo = false
        }
    }

    fun desconectarAmigo(amigo: Pessoa) {
        scope.launch {
            try {
                amigos.remover(amigo.amigoUid)
                repository.excluirPessoa(amigo.id)
                recarregar()
                navController.navigateUp()
                avisar("Você desconectou de ${amigo.nome}.")
            } catch (e: Exception) {
                avisar("Não consegui desconectar. Veja a internet.")
            }
        }
    }

    fun responderConvite(convite: Convite, aceitar: Boolean) {
        if (aceitar) salvarEvento(eventoDoConvite(convite, pessoas), convidar = false)
        convites.remove(convite)
        scope.launch {
            try {
                amigos.apagarConvite(convite.id)
            } catch (e: Exception) {
                // Na próxima sincronização o convite volta a aparecer
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedVisibility(
                visible = mostrarAbas,
                enter = expandVertically(tween(DURACAO_BARRAS), expandFrom = Alignment.Top) + fadeIn(tween(DURACAO_BARRAS)),
                exit = shrinkVertically(tween(DURACAO_BARRAS), shrinkTowards = Alignment.Top) + fadeOut(tween(DURACAO_BARRAS))
            ) {
                BarraAssistente(
                    nome = voce.nome,
                    onAbrir = { navController.navigate(Rotas.assistente()) },
                    onAudio = { navController.navigate(Rotas.assistente(audio = true)) },
                    onPerfil = { navController.navigate(Rotas.PERFIL) }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = mostrarAbas,
                // Sem recorte: o botão de criar passa da barra e não pode ser cortado na animação
                enter = expandVertically(tween(DURACAO_BARRAS), expandFrom = Alignment.Bottom, clip = false) + fadeIn(tween(DURACAO_BARRAS)),
                exit = shrinkVertically(tween(DURACAO_BARRAS), shrinkTowards = Alignment.Bottom, clip = false) + fadeOut(tween(DURACAO_BARRAS))
            ) {
                BarraAbas(
                    rotaAtual = ultimaAba,
                    onIr = { navController.irParaAba(it) },
                    onCriar = { navController.navigate(Rotas.novoEvento()) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = destinoInicial,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            enterTransition = { entradaDaTela(voltando = false) },
            exitTransition = { saidaDaTela(voltando = false) },
            popEnterTransition = { entradaDaTela(voltando = true) },
            popExitTransition = { saidaDaTela(voltando = true) }
        ) {
            composable(Rotas.BOAS_VINDAS) {
                TelaBoasVindas(
                    comConta = nuvem.configurada,
                    entrando = entrando,
                    erro = erroEntrada,
                    aviso = avisoEntrada,
                    onEntrar = ::entrarComGoogle,
                    onEntrarEmail = { email, senha, criar ->
                        entrarCom { if (criar) nuvem.criarConta(email, senha) else nuvem.entrarComEmail(email, senha) }
                    },
                    onEsqueciSenha = ::esqueciSenha,
                    onComecar = { navController.navigate(Rotas.CONFIGURAR) }
                )
            }

            composable(Rotas.CONFIGURAR) {
                TelaFormularioEscala(
                    modo = ModoEscala.CONFIGURACAO,
                    inicial = null,
                    nomeConta = conta?.nome.orEmpty(),
                    onVoltar = { navController.navigateUp() },
                    onSalvar = { pessoa ->
                        salvarPerfil(pessoa) {
                            irParaInicio()
                            navController.navigate(Rotas.APRESENTACAO)
                        }
                    }
                )
            }

            composable(Rotas.APRESENTACAO) {
                TelaApresentacao(
                    modoFacil = carregado.modoFacil,
                    onModoFacil = ::mudarModoFacil,
                    onConcluir = { navController.navigateUp() }
                )
            }

            composable(Rotas.INICIO) {
                TelaInicio(
                    voce = voce,
                    pessoas = pessoas,
                    eventos = eventos,
                    feriados = feriados,
                    convites = convites,
                    snackbar = snackbar,
                    onResponderConvite = ::responderConvite,
                    onResponderFeriado = ::responderFeriado,
                    onAbrirDia = { navController.navigate(Rotas.dia(it)) },
                    onAbrirEvento = { navController.navigate(Rotas.evento(it)) },
                    onNovaPessoa = { navController.navigate(Rotas.pessoa(0)) },
                    onNovoEvento = { navController.navigate(Rotas.novoEvento(it)) },
                    onPlanejar = { e -> navController.navigate(Rotas.novoEvento(e.dia, e.janela.inicio, pessoas.map { it.id }.toSet())) },
                    onQuandoDa = { navController.navigate(Rotas.QUANDO_DA) },
                    onVerPessoas = { navController.irParaAba(Rotas.PESSOAS) },
                    onPerguntarIA = { navController.navigate(Rotas.assistente()) }
                )
            }

            composable(Rotas.QUANDO_DA) {
                TelaQuandoDa(
                    todas = todas,
                    feriados = feriados,
                    onVoltar = { navController.navigateUp() },
                    onAbrirDia = { navController.navigate(Rotas.dia(it)) },
                    onPlanejar = { encontro, quem ->
                        navController.navigate(Rotas.novoEvento(encontro.dia, encontro.janela.inicio, quem))
                    }
                )
            }

            composable(Rotas.CALENDARIO) {
                TelaCalendario(
                    voce = voce,
                    pessoas = pessoas,
                    eventos = eventos,
                    agenda = agenda,
                    feriados = feriados,
                    cargaFeriados = cargaFeriados,
                    snackbar = snackbar,
                    visao = carregado.visao,
                    onMudarVisao = { v -> scope.launch { context.dataStore.edit { it[CHAVE_VISAO] = v.name } } },
                    onCarregarAno = ::carregarFeriados,
                    onAbrirDia = { navController.navigate(Rotas.dia(it)) },
                    onEditarSono = { editandoSono = true }
                )
            }

            composable(Rotas.PESSOAS) {
                TelaPessoas(
                    voce = voce,
                    pessoas = pessoas,
                    snackbar = snackbar,
                    cartaoAmigos = { modifier ->
                        if (nuvem.configurada) {
                            CartaoAmigos(
                                temConta = conta != null,
                                codigo = codigoAmigo,
                                falhou = amigosFalhou,
                                onEntrar = ::entrarComGoogle,
                                onCompartilhar = { compartilharCodigo(context, it) },
                                onAdicionar = {
                                    erroAmigo = null
                                    adicionandoAmigo = true
                                },
                                modifier = modifier
                            )
                        }
                    },
                    onAbrirPerfil = { navController.navigate(Rotas.PERFIL) },
                    onAbrirPessoa = { navController.navigate(Rotas.pessoa(it)) },
                    onNovaPessoa = { navController.navigate(Rotas.pessoa(0)) }
                )
            }

            composable(Rotas.EVENTOS) {
                TelaEventos(
                    eventos = eventos,
                    todas = todas,
                    agenda = agenda,
                    agendaConectada = agendaConectada,
                    grupos = grupos,
                    snackbar = snackbar,
                    onAbrirEvento = { navController.navigate(Rotas.evento(it)) },
                    onAbrirGrupo = { navController.navigate(Rotas.grupo(it)) },
                    onNovoGrupo = { criandoGrupo = true },
                    onConectarAgenda = ::conectarAgenda,
                    onDesconectarAgenda = ::desconectarAgenda
                )
            }

            composable(
                Rotas.ASSISTENTE,
                arguments = listOf(
                    navArgument("audio") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                TelaAssistente(
                    todas = todas,
                    eventos = eventos,
                    mensagens = mensagensIA,
                    pensando = pensando,
                    iniciarGravando = entry.arguments?.getBoolean("audio") == true,
                    grupos = grupos,
                    plano = plano,
                    analises = analisesPlano,
                    criandoPlano = criandoPlano,
                    agendaLiberada = agendaRepository.temEscrita(),
                    agendaGoogle = agendaGoogle,
                    onMudarPlano = ::mudarPlano,
                    onCriarPlano = ::criarPlano,
                    onConectarAgenda = ::conectarAgenda,
                    snackbar = snackbar,
                    onVoltar = { navController.navigateUp() },
                    onEnviar = { pedirParaIA(it, null, null) },
                    onEnviarAudio = { arquivo, segundos -> pedirParaIA(null, arquivo, segundos) },
                    onAplicar = ::aplicarMensagemIA,
                    onDescartar = { i -> mensagensIA[i] = mensagensIA[i].copy(situacao = SituacaoAcoes.DESCARTADAS) },
                    onLimpar = {
                        mensagensIA.clear()
                        mudarPlano(null)
                    }
                )
            }

            composable(
                Rotas.GRUPO,
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                TelaGrupo(
                    grupo = grupos.firstOrNull { it.id == id },
                    grupos = grupos,
                    eventos = eventos,
                    todas = todas,
                    compromissos = agenda.values.flatten(),
                    snackbar = snackbar,
                    onVoltar = { navController.navigateUp() },
                    onAbrirEvento = { navController.navigate(Rotas.evento(it)) },
                    onNovoEvento = { navController.navigate(Rotas.novoEvento(grupo = id)) },
                    onEditar = ::editarGrupo,
                    onExcluir = ::excluirGrupo,
                    onMover = ::moverEventos,
                    onImportar = ::importarCompromisso,
                    onRestaurar = ::restaurarEvento,
                    onExcluirEvento = { e ->
                        AlarmScheduler.cancel(context, e.id)
                        repository.excluirEvento(e.id)
                        recarregar()
                    }
                )
            }

            composable(Rotas.PERFIL) {
                TelaFormularioEscala(
                    modo = ModoEscala.PERFIL,
                    inicial = voce,
                    extraInicio = {
                        if (nuvem.configurada) LinhaConta(conta?.email, entrando, ::entrarComGoogle, ::sairDaConta)
                        LinhaModoFacil(carregado.modoFacil, ::mudarModoFacil)
                        TextButton(onClick = { navController.navigate(Rotas.APRESENTACAO) }) { Text("Ver a apresentação") }
                    },
                    onVoltar = { navController.navigateUp() },
                    onSalvar = { pessoa ->
                        salvarPerfil(pessoa) {
                            navController.navigateUp()
                            avisar("Sua escala foi atualizada.")
                        }
                    }
                )
            }

            composable(
                Rotas.PESSOA,
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                // Valor fixado na entrada: a tela não muda de modo enquanto sai após salvar ou excluir
                val existente = remember { pessoas.firstOrNull { it.id == id } }
                if (existente != null && existente.amigoUid.isNotEmpty()) {
                    TelaAmigo(
                        amigo = existente,
                        onVoltar = { navController.navigateUp() },
                        onDesfazer = { desconectarAmigo(existente) }
                    )
                } else {
                    TelaFormularioEscala(
                        modo = if (existente == null) ModoEscala.NOVA_PESSOA else ModoEscala.EDITAR_PESSOA,
                        inicial = existente,
                        onVoltar = { navController.navigateUp() },
                        onSalvar = { pessoa -> salvarPessoa(pessoa, existente) },
                        onExcluir = existente?.let { p -> { excluirPessoa(p) } }
                    )
                }
            }

            composable(
                Rotas.DIA,
                arguments = listOf(navArgument("dia") { type = NavType.IntType })
            ) { entry ->
                TelaDia(
                    diaInicial = entry.arguments?.getInt("dia") ?: hoje(),
                    todas = todas,
                    eventos = eventos,
                    agenda = agenda,
                    feriados = feriados,
                    snackbar = snackbar,
                    onCarregarAno = ::carregarFeriados,
                    onVoltar = { navController.navigateUp() },
                    onAbrirEvento = { navController.navigate(Rotas.evento(it)) },
                    onNovoEvento = { navController.navigate(Rotas.novoEvento(it)) },
                    onMudarDia = ::mudarDia
                )
            }

            composable(
                Rotas.FORM_EVENTO,
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("dia") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("hora") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("quem") {
                        type = NavType.StringType
                        defaultValue = "*"
                    },
                    navArgument("grupo") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                val dia = entry.arguments?.getInt("dia") ?: -1
                val hora = entry.arguments?.getInt("hora") ?: -1
                TelaFormularioEvento(
                    inicial = remember { eventos.firstOrNull { it.id == id } },
                    diaSugerido = dia.takeIf { it >= 0 },
                    horaSugerida = hora.takeIf { it >= 0 },
                    participantesSugeridos = textoParaParticipantes(entry.arguments?.getString("quem") ?: "*"),
                    todas = todas,
                    agenda = agenda,
                    feriados = feriados,
                    grupos = grupos,
                    grupoSugerido = entry.arguments?.getInt("grupo") ?: 0,
                    onNovoGrupo = ::criarGrupo,
                    onResponderFeriado = ::responderFeriado,
                    onVoltar = { navController.navigateUp() },
                    onSalvar = { evento ->
                        if (salvarEvento(evento)) navController.navigateUp()
                    }
                )
            }

            composable(
                Rotas.EVENTO,
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                TelaDetalheEvento(
                    evento = eventos.firstOrNull { it.id == id },
                    todas = todas,
                    eventos = eventos,
                    feriados = feriados,
                    grupos = grupos,
                    snackbar = snackbar,
                    onVoltar = {
                        // Aberto pela notificação, não há tela anterior: volta para o início
                        if (!navController.navigateUp()) navController.irParaAba(Rotas.INICIO)
                    },
                    onEditar = { navController.navigate(Rotas.editarEvento(id)) },
                    onRemarcar = ::remarcarEvento,
                    onExcluir = ::excluirEvento,
                    onRestaurar = ::restaurarEvento,
                    onAbrirGrupo = { navController.navigate(Rotas.grupo(it)) },
                    onAvisar = ::avisar
                )
            }
        }
    }

    copiaEmConflito?.let { copia ->
        DialogoConflitoNuvem(
            onUsarConta = {
                copiaEmConflito = null
                aplicarCopia(copia) {
                    sairDoLogin()
                    avisar("Seus dados voltaram.")
                }
            },
            onManterCelular = {
                copiaEmConflito = null
                nuvemPronta = true
                sairDoLogin()
            }
        )
    }

    if (adicionandoAmigo) {
        DialogoCodigoAmigo(
            erro = erroAmigo,
            ocupado = conectandoAmigo,
            onConfirmar = ::conectarAmigo,
            onFechar = { adicionandoAmigo = false }
        )
    }

    if (criandoGrupo) {
        DialogoGrupo(
            inicial = null,
            grupos = grupos,
            onSalvar = { nome, cor ->
                criandoGrupo = false
                criarGrupo(nome, cor)
            },
            onFechar = { criandoGrupo = false }
        )
    }

    if (editandoSono) {
        DialogoSono(
            sono = voce.sono,
            turnos = voce.todosTurnos,
            onSalvar = {
                editandoSono = false
                salvarSono(it)
            },
            onFechar = { editandoSono = false }
        )
    }
}
