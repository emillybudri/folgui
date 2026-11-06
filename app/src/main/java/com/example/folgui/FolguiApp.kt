package com.example.folgui

import android.Manifest
import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.credentials.exceptions.GetCredentialCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ============================================================================
// Persistência de Preferências com DataStore: a escala do próprio usuário
// ============================================================================
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias")

private val CHAVE_NOME = stringPreferencesKey("nome")
private val CHAVE_ESCALA = stringPreferencesKey("escala")
private val CHAVE_INICIO = stringPreferencesKey("inicio_ciclo")
private val CHAVE_FERIAS_INICIO = stringPreferencesKey("ferias_inicio")
private val CHAVE_FERIAS_FIM = stringPreferencesKey("ferias_fim")
private val CHAVE_CIDADE = stringPreferencesKey("cidade")
private val CHAVE_AGENDA = stringPreferencesKey("google_agenda")
private val CHAVE_DIAS_SEMANA = stringPreferencesKey("dias_semana")
private val CHAVE_TURNO_INICIO = stringPreferencesKey("turno_inicio")
private val CHAVE_TURNO_FIM = stringPreferencesKey("turno_fim")
private val CHAVE_PADRAO = stringPreferencesKey("padrao")
private val CHAVE_DESCANSO = stringPreferencesKey("descanso_min")
private val CHAVE_SONO = stringPreferencesKey("sono")
private val CHAVE_VISAO = stringPreferencesKey("visao_calendario")
private val CHAVE_NUVEM_EM = stringPreferencesKey("nuvem_atualizada_em")
private val CHAVE_ATIVIDADES = stringPreferencesKey("atividades")

data class Perfil(
    val nome: String,
    val escala: TipoEscala,
    val inicioCiclo: Int,
    val feriasInicio: Int?,
    val feriasFim: Int?,
    val cidade: String,
    val padrao: List<Turno?>,
    val descansoMin: Int,
    val sono: Turno?,
    val atividades: List<Atividade> = emptyList()
) {
    fun comoPessoa(excecoes: Map<Int, Excecao>) = Pessoa(
        id = Pessoa.ID_VOCE,
        nome = nome,
        escala = escala,
        inicioCiclo = inicioCiclo,
        padrao = padrao,
        feriasInicio = feriasInicio,
        feriasFim = feriasFim,
        descansoMin = descansoMin,
        excecoes = excecoes,
        sono = sono,
        atividades = atividades
    )
}

/** Envolve o perfil para diferenciar "carregando" (null) de "ainda não configurado" (perfil null). */
private data class EstadoPerfil(
    val perfil: Perfil?,
    val agendaConectada: Boolean,
    /** A pessoa negou o acesso ou desconectou: o Folgui não pede de novo nem grava na agenda. */
    val agendaDesligada: Boolean,
    val visao: VisaoCalendario
)

private fun lerPerfil(prefs: Preferences): Perfil? {
    val nome = prefs[CHAVE_NOME] ?: return null
    val inicio = textoParaDia(prefs[CHAVE_INICIO] ?: "") ?: return null
    val escala = TipoEscala.porCodigo(prefs[CHAVE_ESCALA])
    // Perfis antigos com escala 5x2 viram "dias fixos" com os mesmos dias da semana
    val diasSemana = prefs[CHAVE_DIAS_SEMANA]?.toIntOrNull()
        ?: if (prefs[CHAVE_ESCALA] == "5x2") diasSemanaDe5x2(inicio) else SEG_A_SEX
    return Perfil(
        nome = nome,
        escala = escala,
        inicioCiclo = inicio,
        feriasInicio = textoParaDia(prefs[CHAVE_FERIAS_INICIO] ?: ""),
        feriasFim = textoParaDia(prefs[CHAVE_FERIAS_FIM] ?: ""),
        cidade = prefs[CHAVE_CIDADE] ?: "",
        // Perfis da versão anterior guardavam só um turno: monta o ciclo a partir dele
        padrao = textoParaPadrao(prefs[CHAVE_PADRAO] ?: "") ?: padraoLegado(
            escala,
            diasSemana,
            Turno(
                prefs[CHAVE_TURNO_INICIO]?.toIntOrNull() ?: escala.turnoPadrao.inicio,
                prefs[CHAVE_TURNO_FIM]?.toIntOrNull() ?: escala.turnoPadrao.fim
            )
        ),
        descansoMin = prefs[CHAVE_DESCANSO]?.toIntOrNull() ?: 0,
        sono = Turno.deCodigo(prefs[CHAVE_SONO] ?: ""),
        atividades = textoParaAtividades(prefs[CHAVE_ATIVIDADES] ?: "")
    )
}

enum class Carga { CARREGANDO, OK, ERRO }

// ============================================================================
// Navegação entre telas com NavController e NavHost
// ============================================================================
object Rotas {
    const val BOAS_VINDAS = "boas_vindas"
    const val CONFIGURAR = "configurar"
    const val INICIO = "inicio"
    const val CALENDARIO = "calendario"
    const val PESSOAS = "pessoas"
    const val EVENTOS = "eventos"
    const val PERFIL = "perfil"
    const val PESSOA = "pessoa/{id}"
    const val DIA = "dia/{dia}"
    const val FORM_EVENTO = "evento/{id}?dia={dia}&hora={hora}&quem={quem}"
    const val EVENTO = "detalhe_evento/{id}"
    const val QUANDO_DA = "quando_da"
    const val ASSISTENTE = "assistente?audio={audio}"

    fun pessoa(id: Int) = "pessoa/$id"
    fun dia(dia: Int) = "dia/$dia"
    fun novoEvento(dia: Int? = null, hora: Int = -1, quem: Set<Int>? = null) =
        "evento/0?dia=${dia ?: -1}&hora=$hora&quem=${participantesParaTexto(quem)}"
    fun editarEvento(id: Int) = "evento/$id"
    fun evento(id: Int) = "detalhe_evento/$id"
    fun assistente(audio: Boolean = false) = "assistente?audio=$audio"
}

private data class Aba(val rota: String, val rotulo: String, val icone: ImageVector)

private val ABAS = listOf(
    Aba(Rotas.INICIO, "Hoje", Icones.Inicio),
    Aba(Rotas.CALENDARIO, "Agenda", Icones.Calendario),
    Aba(Rotas.EVENTOS, "Eventos", Icones.Evento),
    Aba(Rotas.PESSOAS, "Pessoas", Icones.Pessoas)
)

/** Abas com o botão de criar no centro: a ação mais comum fica sempre à mão. */
@Composable
fun BarraAbas(rotaAtual: String?, onIr: (String) -> Unit, onCriar: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = Espaco.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ABAS.take(2).forEach { ItemAba(it, rotaAtual == it.rota) { onIr(it.rota) } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FilledIconButton(onClick = onCriar, modifier = Modifier.size(52.dp)) {
                    Icon(Icones.Adicionar, contentDescription = "Novo evento")
                }
            }
            ABAS.drop(2).forEach { ItemAba(it, rotaAtual == it.rota) { onIr(it.rota) } }
        }
    }
}

@Composable
private fun RowScope.ItemAba(aba: Aba, selecionada: Boolean, onClick: () -> Unit) {
    val cor = if (selecionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(aba.icone, contentDescription = null, tint = cor)
        Text(
            aba.rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = cor,
            fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun NavHostController.irParaAba(rota: String) {
    navigate(rota) {
        popUpTo(Rotas.INICIO) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun FolguiApp(eventoInicial: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val estadoFlow = remember {
        context.dataStore.data.map {
            EstadoPerfil(
                lerPerfil(it),
                it[CHAVE_AGENDA] == "1",
                it[CHAVE_AGENDA] == "0",
                VisaoCalendario.porNome(it[CHAVE_VISAO])
            )
        }
    }
    val estado by estadoFlow.collectAsState(initial = null)

    // Abertura animada por um instante (não aparece quando o app é aberto pela notificação)
    var abertura by rememberSaveable { mutableStateOf(eventoInicial == 0) }
    LaunchedEffect(Unit) {
        delay(1400)
        abertura = false
    }
    val carregado = estado
    if (abertura || carregado == null) {
        TelaAbertura()
        return
    }

    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val repository = remember { FolguiRepository(context) }
    val api = remember { ApiRepository() }
    val agendaRepository = remember { AgendaRepository(context) }
    val nuvem = remember { NuvemRepository() }

    // O login vem antes de tudo; sem o Firebase configurado, o app abre sem conta
    val destinoInicial = remember {
        val logado = !nuvem.configurada || nuvem.contaAtual != null
        if (carregado.perfil != null && logado) Rotas.INICIO else Rotas.BOAS_VINDAS
    }
    val perfil = carregado.perfil

    // Dados do SQLite (as mudanças na escala do usuário ficam na tabela de exceções com id -1)
    val pessoas = remember { mutableStateListOf<Pessoa>() }
    val eventos = remember { mutableStateListOf<Evento>() }
    val excecoesVoce = remember { mutableStateMapOf<Int, Excecao>() }

    fun recarregar() {
        pessoas.clear()
        pessoas.addAll(repository.listarPessoas())
        eventos.clear()
        eventos.addAll(repository.listarEventos())
        excecoesVoce.clear()
        excecoesVoce.putAll(repository.listarExcecoes()[Pessoa.ID_VOCE].orEmpty())
    }

    val voce = perfil?.comoPessoa(excecoesVoce.toMap()) ?: Pessoa(Pessoa.ID_VOCE, "Você", TipoEscala.SEMANAL, hoje())

    LaunchedEffect(Unit) { recarregar() }

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

    // Feriados nacionais (BrasilAPI), carregados por ano conforme a necessidade
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

    // Compromissos da Google Agenda (lidos só depois que o usuário conecta e permite o acesso)
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

    /** Grava o evento na Google Agenda e guarda o id de lá, para mexer sempre no mesmo evento. */
    fun copiarParaAgenda(evento: Evento): Boolean {
        val id = agendaRepository.gravar(evento, repository.agendaIdDe(evento.id)) ?: return false
        repository.salvarAgendaId(evento.id, id)
        return true
    }

    // Eventos à espera da resposta do acesso à agenda. O Android só aceita um pedido de permissão
    // por vez, então o de notificações espera este terminar.
    val aguardandoAgenda = remember { mutableListOf<Int>() }
    var pedirNotificacaoDepois by remember { mutableStateOf(false) }

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
        if (pedirNotificacaoDepois) {
            pedirNotificacaoDepois = false
            pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Mantém a Google Agenda igual ao Folgui (na primeira vez, pede o acesso). Retorna se já gravou. */
    fun sincronizarAgenda(evento: Evento): Boolean {
        if (carregado.agendaDesligada) return false
        if (agendaRepository.temEscrita()) return copiarParaAgenda(evento)
        if (aguardandoAgenda.isEmpty()) pedirAgenda.launch(PERMISSOES_AGENDA)
        aguardandoAgenda.add(evento.id)
        return false
    }

    /** Chamar antes de excluir o evento do banco, enquanto o vínculo com a agenda ainda existe. */
    fun tirarDaAgenda(evento: Evento) {
        if (!carregado.agendaDesligada) agendaRepository.excluir(repository.agendaIdDe(evento.id))
    }

    // Navega só depois da gravação, para a próxima tela já mostrar os dados novos
    fun salvarPerfil(pessoa: Pessoa, cidade: String, depois: () -> Unit) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[CHAVE_NOME] = pessoa.nome
                prefs[CHAVE_ESCALA] = pessoa.escala.codigo
                prefs[CHAVE_INICIO] = diaParaTexto(pessoa.inicioCiclo)
                prefs[CHAVE_FERIAS_INICIO] = pessoa.feriasInicio?.let(::diaParaTexto) ?: ""
                prefs[CHAVE_FERIAS_FIM] = pessoa.feriasFim?.let(::diaParaTexto) ?: ""
                prefs[CHAVE_CIDADE] = cidade
                prefs[CHAVE_DIAS_SEMANA] = pessoa.diasSemana.toString()
                prefs[CHAVE_TURNO_INICIO] = (pessoa.turnos.firstOrNull() ?: pessoa.escala.turnoPadrao).inicio.toString()
                prefs[CHAVE_TURNO_FIM] = (pessoa.turnos.firstOrNull() ?: pessoa.escala.turnoPadrao).fim.toString()
                prefs[CHAVE_PADRAO] = padraoParaTexto(pessoa.padrao)
                prefs[CHAVE_DESCANSO] = pessoa.descansoMin.toString()
                prefs[CHAVE_SONO] = pessoa.sono?.codigo ?: ""
                prefs[CHAVE_ATIVIDADES] = atividadesParaTexto(pessoa.atividades)
            }
            depois()
        }
    }

    // Conta do Google: a cópia na nuvem só é enviada depois de comparada com o que já existe lá,
    // para um aparelho desatualizado não apagar o que foi feito em outro
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
        repository.substituirTudo(dados.pessoas, dados.excecoesVoce, dados.eventos)
        dados.eventos.forEach { AlarmScheduler.schedule(context, it) }
        ultimoEnviado = copia.dados
        salvarPerfil(dados.perfil.comoPessoa(emptyMap()), dados.perfil.cidade) {
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
        dadosParaTexto(DadosNuvem(it, excecoesVoce.toMap(), pessoas.toList(), eventos.toList()))
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

    // Horário de sono, editado direto pela tela inicial ou pela semana
    var editandoSono by rememberSaveable { mutableStateOf(false) }

    fun salvarSono(sono: Turno?) {
        val anterior = voce
        val cidade = perfil?.cidade ?: ""
        salvarPerfil(voce.copy(sono = sono), cidade) {}
        avisar(
            sono?.let { "Sono das ${it.resumo} salvo." } ?: "Horário de sono removido.",
            acao = "Desfazer"
        ) { salvarPerfil(anterior, cidade) {} }
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

    fun salvarEvento(evento: Evento): Boolean {
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
        recarregar()

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
                val momento = salvo.momentoLembrete!!
                "$resultado Lembrete em ${diaResumido(diaDoMomento(momento))} às ${horarioDoMomento(momento)}."
            } else {
                resultado
            }
        )
        return true
    }

    LaunchedEffect(Unit) {
        if (eventoInicial > 0 && perfil != null) navController.navigate(Rotas.evento(eventoInicial))
    }

    // ------------------------------------------------------------------
    // Assistente com IA: a conversa fica na memória enquanto o app está aberto
    // ------------------------------------------------------------------
    val assistente = remember { AssistenteIA(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL) }
    val mensagensIA = remember { mutableStateListOf<MensagemIA>() }
    var pensando by remember { mutableStateOf(false) }

    /** Envia um pedido escrito ou gravado. A resposta passa pela conferência do app antes de aparecer. */
    fun pedirParaIA(pedido: String?, audio: File?, segundos: Int?) {
        val historico = mensagensIA.toList()
        val pessoasAgora = todas
        val eventosAgora = eventos.toList()
        val contexto = contextoParaIA(pessoasAgora, eventosAgora, agenda.toMap(), feriados.toMap())
        val indice = mensagensIA.size
        mensagensIA.add(MensagemIA(deUsuario = true, texto = pedido ?: "Áudio enviado", audioSegundos = segundos))
        pensando = true
        scope.launch {
            val resposta = try {
                val base64 = audio?.let { arquivo ->
                    withContext(Dispatchers.IO) { Base64.encodeToString(arquivo.readBytes(), Base64.NO_WRAP) }
                }
                val r = assistente.conversar(contexto, historico, pessoasAgora, eventosAgora, pedido, base64)
                // O texto do áudio fica visível: é a primeira forma de conferir se a IA ouviu certo
                if (audio != null && r.transcricao.isNotBlank()) {
                    mensagensIA[indice] = mensagensIA[indice].copy(texto = r.transcricao)
                }
                val (validas, avisos) = verificarAcoes(r.mensagem.acoes, pessoasAgora, eventosAgora)
                r.mensagem.copy(
                    acoes = validas,
                    avisos = r.mensagem.avisos + avisos,
                    descricoes = validas.map { descreverAcao(it, pessoasAgora, eventosAgora) }
                )
            } catch (e: ErroIA) {
                MensagemIA(deUsuario = false, texto = e.message ?: "Algo deu errado.")
            } catch (e: Exception) {
                MensagemIA(deUsuario = false, texto = "Sem conexão com a IA. Verifique a internet e tente de novo.")
            } finally {
                audio?.delete()
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
                    desfazer.add {
                        repository.atualizarEvento(antes)
                        AlarmScheduler.schedule(context, antes)
                        sincronizarAgenda(antes)
                    }
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
        val cidade = perfil?.cidade ?: ""
        if (voceNovo != voce) {
            val antigo = voce
            salvarPerfil(voceNovo, cidade) {}
            desfazer.add { salvarPerfil(antigo, cidade) {} }
        }
        recarregar()
        return {
            desfazer.reversed().forEach { it() }
            recarregar()
        }
    }

    val entrada by navController.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route
    val mostrarAbas = ABAS.any { it.rota == rotaAtual }

    // Recarrega a agenda ao trocar de tela: um evento enviado para a Google Agenda logo aparece aqui
    LaunchedEffect(agendaConectada, rotaAtual) {
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
        } else {
            agendaLida.clear()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (mostrarAbas) {
                BarraAssistente(
                    nome = voce.nome,
                    onAbrir = { navController.navigate(Rotas.assistente()) },
                    onAudio = { navController.navigate(Rotas.assistente(audio = true)) },
                    onPerfil = { navController.navigate(Rotas.PERFIL) }
                )
            }
        },
        bottomBar = {
            if (mostrarAbas) {
                BarraAbas(
                    rotaAtual = rotaAtual,
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
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) }
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
                    cidadeInicial = "",
                    onVoltar = { navController.navigateUp() },
                    onSalvar = { pessoa, cidade ->
                        salvarPerfil(pessoa, cidade) {
                            irParaInicio()
                            avisar("Tudo pronto!")
                        }
                    }
                )
            }

            composable(Rotas.INICIO) {
                TelaInicio(
                    voce = voce,
                    pessoas = pessoas,
                    eventos = eventos,
                    feriados = feriados,
                    snackbar = snackbar,
                    onResponderFeriado = ::responderFeriado,
                    onAbrirDia = { navController.navigate(Rotas.dia(it)) },
                    onAbrirEvento = { navController.navigate(Rotas.evento(it)) },
                    onNovaPessoa = { navController.navigate(Rotas.pessoa(0)) },
                    onNovoEvento = { navController.navigate(Rotas.novoEvento(it)) },
                    onPlanejar = { e -> navController.navigate(Rotas.novoEvento(e.dia, e.janela.inicio)) },
                    onQuandoDa = { navController.navigate(Rotas.QUANDO_DA) },
                    onVerPessoas = { navController.irParaAba(Rotas.PESSOAS) },
                    onEditarSono = { editandoSono = true }
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
                    snackbar = snackbar,
                    onAbrirEvento = { navController.navigate(Rotas.evento(it)) },
                    onConectarAgenda = {
                        if (agendaRepository.temEscrita()) {
                            gravarAgendaConectada(true)
                            avisar("Google Agenda conectada.")
                        } else {
                            pedirAgenda.launch(PERMISSOES_AGENDA)
                        }
                    },
                    onDesconectarAgenda = {
                        gravarAgendaConectada(false)
                        avisar("Google Agenda desconectada.")
                    }
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
                    snackbar = snackbar,
                    onVoltar = { navController.navigateUp() },
                    onEnviar = { pedirParaIA(it, null, null) },
                    onEnviarAudio = { arquivo, segundos -> pedirParaIA(null, arquivo, segundos) },
                    onAplicar = { i ->
                        val m = mensagensIA[i]
                        val desfazer = aplicarAcoes(m.acoes)
                        mensagensIA[i] = m.copy(situacao = SituacaoAcoes.APLICADAS)
                        avisar("Mudanças aplicadas.", acao = "Desfazer") {
                            desfazer()
                            mensagensIA[i] = m.copy(situacao = SituacaoAcoes.PENDENTE)
                        }
                    },
                    onDescartar = { i -> mensagensIA[i] = mensagensIA[i].copy(situacao = SituacaoAcoes.DESCARTADAS) },
                    onLimpar = { mensagensIA.clear() }
                )
            }

            composable(Rotas.PERFIL) {
                TelaFormularioEscala(
                    modo = ModoEscala.PERFIL,
                    inicial = voce,
                    cidadeInicial = perfil?.cidade ?: "",
                    extraInicio = if (nuvem.configurada) {
                        { LinhaConta(conta?.email, entrando, ::entrarComGoogle, ::sairDaConta) }
                    } else {
                        null
                    },
                    onVoltar = { navController.navigateUp() },
                    onSalvar = { pessoa, cidade ->
                        salvarPerfil(pessoa, cidade) {
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
                TelaFormularioEscala(
                    modo = if (existente == null) ModoEscala.NOVA_PESSOA else ModoEscala.EDITAR_PESSOA,
                    inicial = existente,
                    cidadeInicial = null,
                    onVoltar = { navController.navigateUp() },
                    onSalvar = { pessoa, _ ->
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
                    },
                    onExcluir = existente?.let { p ->
                        {
                            repository.excluirPessoa(p.id)
                            navController.navigateUp()
                            recarregar()
                            avisar("${p.nome} foi removida.", acao = "Desfazer") {
                                repository.inserirPessoa(p, manterId = true)
                                p.excecoes.values.forEach { repository.salvarExcecao(p.id, it) }
                                recarregar()
                            }
                        }
                    }
                )
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
                    cidadePadrao = perfil?.cidade ?: "",
                    todas = todas,
                    agenda = agenda,
                    feriados = feriados,
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
                    api = api,
                    snackbar = snackbar,
                    onVoltar = {
                        // Aberto pela notificação, não há tela anterior: volta para o início
                        if (!navController.navigateUp()) navController.irParaAba(Rotas.INICIO)
                    },
                    onEditar = { navController.navigate(Rotas.editarEvento(id)) },
                    onRemarcar = ::remarcarEvento,
                    onExcluir = { evento ->
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
                    },
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

    if (editandoSono) {
        DialogoSono(
            sono = voce.sono,
            onSalvar = {
                editandoSono = false
                salvarSono(it)
            },
            onFechar = { editandoSono = false }
        )
    }
}
