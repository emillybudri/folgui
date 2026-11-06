package com.example.folgui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ============================================================================
// FORMULÁRIO DE EVENTO (SQLite + agendamento do lembrete)
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaFormularioEvento(
    inicial: Evento?,
    diaSugerido: Int?,
    horaSugerida: Int?,
    participantesSugeridos: Set<Int>?,
    todas: List<Pessoa>,
    agenda: Map<Int, List<CompromissoAgenda>>,
    feriados: Map<Int, String>,
    onResponderFeriado: (Int, Boolean) -> Unit,
    onVoltar: () -> Unit,
    onSalvar: (Evento) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rolagem = rememberScrollState()
    val diaPadrao = remember {
        diaSugerido ?: diasEmComum(todas, hoje(), quantidade = 1).firstOrNull() ?: hoje()
    }
    // Só posiciona o controle de horas: nada é avaliado até a pessoa escolher a hora ([temHorario])
    val horarioPadrao = remember {
        val noite = Janela(20 * 60, 120)
        if (todosLivres(todas, diaPadrao, noite)) "20:00"
        else janelasLivres(todas, diaPadrao, minimo = 120).firstOrNull()?.let { horarioDeMinutos(it.inicio) } ?: "20:00"
    }

    var titulo by rememberSaveable { mutableStateOf(inicial?.titulo ?: "") }
    var dia by rememberSaveable { mutableIntStateOf(inicial?.dia ?: diaPadrao) }
    var horario by rememberSaveable {
        mutableStateOf(inicial?.horario ?: horaSugerida?.let(::horarioDeMinutos) ?: horarioPadrao)
    }
    var temHorario by rememberSaveable { mutableStateOf(inicial != null || horaSugerida != null) }
    // Quem vai: "*" = todas as pessoas; senão, os ids separados por vírgula
    var quem by rememberSaveable {
        mutableStateOf(participantesParaTexto(if (inicial != null) inicial.participantes else participantesSugeridos ?: emptySet()))
    }
    var local by rememberSaveable { mutableStateOf(inicial?.local ?: "") }
    var antecedencia by rememberSaveable { mutableIntStateOf(inicial?.antecedenciaMin ?: 60) }
    var duracao by rememberSaveable { mutableIntStateOf(inicial?.duracaoMin ?: SEM_FIM) }
    var tentouSalvar by rememberSaveable { mutableStateOf(false) }
    var confirmarSono by rememberSaveable { mutableStateOf(false) }

    val rascunho = Evento(
        id = inicial?.id ?: 0,
        titulo = titulo.trim(),
        dia = dia,
        horario = horario,
        local = local.trim(),
        antecedenciaMin = antecedencia,
        duracaoMin = duracao,
        participantes = textoParaParticipantes(quem)
    )
    val quemVai = pessoasDoEvento(rascunho, todas)
    val outras = todas.filter { !it.ehVoce }

    fun alternarPessoa(id: Int) {
        val atuais = textoParaParticipantes(quem) ?: outras.map { it.id }.toSet()
        val novos = if (id in atuais) atuais - id else atuais + id
        quem = participantesParaTexto(if (novos == outras.map { it.id }.toSet()) null else novos)
    }
    val agora = System.currentTimeMillis()

    // Validação: os erros aparecem só depois da primeira tentativa de salvar
    val erroTitulo = "Dê um nome".takeIf { titulo.isBlank() }
    val erroData = "Escolha hoje ou depois".takeIf { dia < hoje() }
    val erroHorario = when {
        !temHorario -> "Escolha a hora"
        erroData == null && rascunho.momento <= agora -> "Esse horário já passou"
        else -> null
    }
    val erroLembrete = "Aviso no passado".takeIf {
        erroData == null && erroHorario == null && rascunho.momentoLembrete?.let { it <= agora } == true
    }
    val valido = listOf(erroTitulo, erroData, erroHorario, erroLembrete).all { it == null }

    fun salvar() {
        tentouSalvar = true
        if (valido) {
            // Marcar no horário de sono só com uma confirmação explícita
            val noSono = quemVai.any { it.ehVoce && it.motivoOcupado(dia, rascunho.janela) == TipoBloco.SONO }
            if (noSono) confirmarSono = true else onSalvar(rascunho)
        } else if (erroTitulo != null || erroData != null) {
            // O erro da hora aparece perto do botão: rolar para o topo o esconderia
            scope.launch { rolagem.animateScrollTo(0) }
        }
    }

    fun erroVisivel(erro: String?) = erro.takeIf { tentouSalvar }

    // Disponibilidade no horário do evento (turnos de dia e de noite contam)
    val janela = rascunho.janela
    val livres = temHorario && todosLivres(quemVai, dia, janela)
    val inicioPossivel = if (dia == hoje()) ((minutoDoDiaAtual() + 59) / 60) * 60 else 0
    val horariosDoDia = janelasLivres(quemVai, dia, de = maxOf(7 * 60, inicioPossivel), minimo = rascunho.janela.duracao)
    val melhores = remember(quemVai, duracao) { melhoresDias(quemVai, rascunho.janela.duracao) }
    val horariosLivres = if (livres) emptyList() else horariosDoDia.take(3)
    val sugestao = if (horariosDoDia.isEmpty()) melhores.firstOrNull { it != dia } else null
    val voce = todas.first { it.ehVoce }
    val compromissosDoDia = agenda[dia].orEmpty().filter { it.titulo != inicial?.titulo }

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = if (inicial == null) "Novo evento" else "Editar evento",
                onVoltar = onVoltar,
                iconeVoltar = Icones.Fechar
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rolagem),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .limitarLargura()
                    .padding(horizontal = Espaco.lg, vertical = Espaco.sm),
                verticalArrangement = Arrangement.spacedBy(Espaco.xl)
            ) {
                Secao("Evento") {
                    CampoTexto(
                        valor = titulo,
                        onValorChange = { titulo = it.take(60) },
                        rotulo = "Nome",
                        erro = erroVisivel(erroTitulo),
                        modifier = Modifier.fillMaxWidth()
                    )
                    CampoTexto(
                        valor = local,
                        onValorChange = { local = it.take(60) },
                        rotulo = "Local (opcional)",
                        icone = Icones.Local,
                        capitalizacao = KeyboardCapitalization.Words,
                        acaoTeclado = ImeAction.Done,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (outras.isNotEmpty()) {
                    Secao("Com quem") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
                            verticalArrangement = Arrangement.spacedBy(Espaco.xs)
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                enabled = false,
                                label = { Text("Você") }
                            )
                            outras.forEach { p ->
                                val vai = quemVai.any { it.id == p.id }
                                FilterChip(
                                    selected = vai,
                                    onClick = { alternarPessoa(p.id) },
                                    label = { Text(p.nome) },
                                    leadingIcon = {
                                        Avatar(p.nome, tamanho = 20.dp, cores = coresPessoa(p))
                                    }
                                )
                            }
                        }
                    }
                }

                Secao("Dia") {
                    MelhoresDias(
                        pessoas = quemVai,
                        dias = melhores,
                        duracao = rascunho.janela.duracao,
                        selecionado = dia,
                        onEscolher = { dia = it }
                    )
                    CalendarioEscolha(
                        dia = dia,
                        pessoas = quemVai,
                        duracao = rascunho.janela.duracao,
                        feriados = feriados,
                        onEscolher = { dia = it }
                    )
                    erroVisivel(erroData)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    feriados[dia]?.let { nome ->
                        if (voce.perguntarFeriado(dia)) {
                            PerguntaFeriado(dia, nome, onResponderFeriado)
                        } else {
                            SeloFeriado(nome)
                        }
                    }
                    // Quem trabalha aparece antes de escolher a hora, não depois
                    quemVai.filter { it.status(dia) == StatusDia.TRABALHO }.forEach { p ->
                        Text(
                            "${p.nomeCurto}: ${p.descricaoDia(dia)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = corConteudo(StatusDia.TRABALHO)
                        )
                    }
                }

                Secao("Hora") {
                    SeletorHorario(
                        dia = dia,
                        pessoas = quemVai,
                        inicio = janela.inicio,
                        duracao = duracao,
                        escolhido = temHorario,
                        onMudar = { novoInicio, novaDuracao ->
                            horario = horarioDeMinutos(novoInicio)
                            duracao = novaDuracao
                            temHorario = true
                        },
                        onHorarioExato = {
                            abrirSeletorHorario(context, horario) {
                                horario = it
                                temHorario = true
                            }
                        }
                    )
                    erroVisivel(erroHorario)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    compromissosDoDia.forEach {
                        Text(
                            "${it.horario} · ${it.titulo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    horariosLivres.forEach { livre ->
                        LinhaSugestao("Livre ${horaCurta(livre.inicio)} às ${horaCurta(livre.fim)}") {
                            horario = horarioDeMinutos(livre.inicio)
                            temHorario = true
                        }
                    }
                    if (sugestao != null) {
                        LinhaSugestao("Livre ${diaResumido(sugestao)}") { dia = sugestao }
                    }
                }

                Secao("Aviso") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
                        verticalArrangement = Arrangement.spacedBy(Espaco.xs)
                    ) {
                        OPCOES_LEMBRETE.forEach { opcao ->
                            val selecionada = antecedencia == opcao.minutos
                            FilterChip(
                                selected = selecionada,
                                onClick = { antecedencia = opcao.minutos },
                                label = { Text(opcao.rotulo) },
                                leadingIcon = if (selecionada) {
                                    { Icon(Icones.Confirmar, contentDescription = null, Modifier.size(18.dp)) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                    val erro = erroVisivel(erroLembrete)
                    val momentoAviso = rascunho.momentoLembrete
                    Text(
                        text = when {
                            erro != null -> erro
                            momentoAviso == null -> "Sem aviso"
                            else -> "${diaResumido(diaDoMomento(momentoAviso))} às ${horarioDoMomento(momentoAviso)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (erro != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = ::salvar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text("Salvar")
                }
                Spacer(Modifier.height(Espaco.lg))
            }
        }
    }

    if (confirmarSono) {
        AlertDialog(
            onDismissRequest = { confirmarSono = false },
            title = { Text("Horário de sono") },
            text = { Text("Sono: ${todas.firstOrNull { it.ehVoce }?.sono?.resumo}") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSono = false
                    onSalvar(rascunho)
                }) { Text("Marcar") }
            },
            dismissButton = { TextButton(onClick = { confirmarSono = false }) { Text("Trocar") } }
        )
    }
}

// ============================================================================
// DETALHE DO EVENTO: disponibilidade e lembrete
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDetalheEvento(
    evento: Evento?,
    todas: List<Pessoa>,
    eventos: List<Evento>,
    feriados: Map<Int, String>,
    snackbar: SnackbarHostState,
    onVoltar: () -> Unit,
    onEditar: () -> Unit,
    onRemarcar: (Evento, Evento) -> Unit,
    onExcluir: (Evento) -> Unit,
    onAvisar: (String) -> Unit
) {
    val context = LocalContext.current
    // Mantém o último evento exibido para a tela não piscar enquanto sai após a exclusão
    var exibido by remember { mutableStateOf(evento) }
    LaunchedEffect(evento) { if (evento != null) exibido = evento }
    var confirmarExclusao by rememberSaveable { mutableStateOf(false) }
    var remarcando by rememberSaveable { mutableStateOf(false) }

    val atual = exibido

    Scaffold(
        topBar = {
            BarraSuperior(titulo = "Evento", onVoltar = onVoltar) {
                if (atual != null) {
                    IconButton(onClick = { confirmarExclusao = true }) {
                        Icon(Icones.Excluir, contentDescription = "Excluir evento")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        if (atual == null) {
            EstadoVazio(
                icone = Icones.Evento,
                titulo = "Evento não encontrado",
                texto = "Ele pode ter sido excluído.",
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        val momentoAviso = atual.momentoLembrete
        val quemVai = pessoasDoEvento(atual, todas)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .limitarLargura()
                    .padding(horizontal = Espaco.lg, vertical = Espaco.sm),
                verticalArrangement = Arrangement.spacedBy(Espaco.lg)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Espaco.md)) {
                    Text(atual.titulo, style = MaterialTheme.typography.headlineMedium)
                    feriados[atual.dia]?.let { SeloFeriado(it) }
                    InfoLinha(Icones.Calendario, diaPorExtenso(atual.dia), rotuloRelativo(atual.dia))
                    InfoLinha(Icones.Relogio, atual.faixaHorario, if (atual.semFim) "Sem hora para acabar" else "Duração de ${atual.duracaoMin / 60}h")
                    if (atual.local.isNotBlank()) InfoLinha(Icones.Local, atual.local)
                    InfoLinha(
                        Icones.Sino,
                        rotuloLembrete(atual.antecedenciaMin),
                        when {
                            momentoAviso == null -> null
                            momentoAviso <= System.currentTimeMillis() -> "Aviso já enviado"
                            else -> "Aviso em ${diaResumido(diaDoMomento(momentoAviso))} às ${horarioDoMomento(momentoAviso)}"
                        }
                    )
                }

                // Vale também para evento que já passou: é assim que se remarca o que não aconteceu
                Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                    Button(onClick = { remarcando = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icones.Calendario, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Espaco.sm))
                        Text("Remarcar")
                    }
                    OutlinedButton(onClick = onEditar, modifier = Modifier.weight(1f)) {
                        Icon(Icones.Editar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Espaco.sm))
                        Text("Editar")
                    }
                }

                CartaoSituacao(pessoas = quemVai, dia = atual.dia, janela = atual.janela)
                LinhaDoTempo(pessoas = quemVai, dia = atual.dia, destaque = atual.janela, titulo = "Dia")
                Cartao(Modifier.fillMaxWidth()) {
                    Column {
                        quemVai.forEachIndexed { i, pessoa ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            LinhaPessoa(pessoa = pessoa, dia = atual.dia, detalhe = pessoa.descricaoDia(atual.dia))
                        }
                    }
                }

                if (!atual.jaPassou) {
                    CartaoCompartilhar(
                        convidados = quemVai.filter { !it.ehVoce && it.email.isNotBlank() },
                        onAgenda = { convidados ->
                            // Já gravado na agenda, abre o evento de lá em vez de criar outro igual
                            val abriu = if (atual.agendaId > 0) {
                                IntegracaoGoogle.abrirCompromisso(
                                    context,
                                    CompromissoAgenda(
                                        titulo = atual.titulo,
                                        inicio = atual.momento,
                                        fim = atual.momento + atual.janela.duracao * 60_000L,
                                        diaInteiro = false,
                                        agenda = "",
                                        eventoId = atual.agendaId
                                    )
                                )
                            } else {
                                IntegracaoGoogle.adicionarNaAgenda(context, atual, convidados)
                            }
                            if (!abriu) onAvisar("Instale a Google Agenda")
                        },
                        onEmail = { convidados ->
                            if (!IntegracaoGoogle.convidarPorEmail(context, atual, convidados)) {
                                onAvisar("Sem app de e-mail")
                            }
                        }
                    )
                }

                Spacer(Modifier.height(Espaco.lg))
            }
        }
    }

    if (remarcando && atual != null) {
        ModalBottomSheet(onDismissRequest = { remarcando = false }) {
            FolhaRemarcar(
                evento = atual,
                opcoes = sugestoesRemarcar(atual, pessoasDoEvento(atual, todas), eventos),
                onEscolher = { opcao ->
                    remarcando = false
                    onRemarcar(atual, atual.copy(dia = opcao.dia, horario = opcao.horario))
                },
                onOutroHorario = {
                    remarcando = false
                    onEditar()
                }
            )
        }
    }

    val paraExcluir = exibido
    if (confirmarExclusao && paraExcluir != null) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Excluir evento?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarExclusao = false
                        onExcluir(paraExcluir)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") }
            }
        )
    }
}

/** Próximos horários livres para o evento, a um toque de distância. */
@Composable
private fun FolhaRemarcar(
    evento: Evento,
    opcoes: List<OpcaoRemarcar>,
    onEscolher: (OpcaoRemarcar) -> Unit,
    onOutroHorario: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Espaco.lg, end = Espaco.lg, bottom = Espaco.xl),
        verticalArrangement = Arrangement.spacedBy(Espaco.sm)
    ) {
        Text("Remarcar", style = MaterialTheme.typography.titleLarge)
        Text(
            "${evento.titulo} · ${diaResumido(evento.dia)}, ${evento.horario}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (opcoes.isEmpty()) {
            Text(
                "Sem horário livre",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Espaco.sm)
            )
        }
        opcoes.forEach { opcao ->
            Cartao(Modifier.fillMaxWidth(), onClick = { onEscolher(opcao) }) {
                Row(Modifier.padding(Espaco.md), verticalAlignment = Alignment.CenterVertically) {
                    SeloData(opcao.dia, destaque = true)
                    Spacer(Modifier.width(Espaco.md))
                    Column(Modifier.weight(1f)) {
                        Text("às ${opcao.horario}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            opcao.motivo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onOutroHorario,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Espaco.alvoToque)
        ) { Text("Outro horário") }
    }
}

/** Sugestão de horário ou dia, aplicada com um toque. */
@Composable
private fun LinhaSugestao(texto: String, onUsar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icones.Brilho, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Espaco.sm))
        Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onUsar) { Text("Usar") }
    }
}

/** Abre o evento na Google Agenda ou convida por e-mail (Gmail). */
@Composable
private fun CartaoCompartilhar(
    convidados: List<Pessoa>,
    onAgenda: (List<String>) -> Unit,
    onEmail: (List<String>) -> Unit
) {
    val emails = convidados.map { it.email }
    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
        FilledTonalButton(onClick = { onAgenda(emails) }, modifier = Modifier.weight(1f)) {
            Icon(Icones.Calendario, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Espaco.sm))
            Text("Agenda", maxLines = 1)
        }
        FilledTonalButton(onClick = { onEmail(emails) }, modifier = Modifier.weight(1f)) {
            Icon(Icones.Email, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Espaco.sm))
            Text("Convidar", maxLines = 1)
        }
    }
}

@Composable
private fun InfoLinha(icone: ImageVector, texto: String, apoio: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Espaco.lg))
        Column {
            Text(texto, style = MaterialTheme.typography.bodyLarge)
            if (apoio != null) {
                Text(apoio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============================================================================
// Escolha do dia e do horário com retorno imediato da disponibilidade
// ============================================================================

/**
 * Calendário grande: cada dia é pintado pelo dia inteiro, sem supor um horário (verde = ninguém
 * trabalha, âmbar = alguém trabalha mas sobra tempo juntos, vermelho = não há esse tempo).
 * Dias passados ficam apagados.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarioEscolha(
    dia: Int,
    pessoas: List<Pessoa>,
    duracao: Int,
    feriados: Map<Int, String>,
    onEscolher: (Int) -> Unit
) {
    val inicial = partes(dia)
    var ano by rememberSaveable { mutableIntStateOf(inicial.ano) }
    var mes by rememberSaveable { mutableIntStateOf(inicial.mes) }
    val diaHoje = hoje()
    val atual = partes(diaHoje)

    // Um atalho de "Melhores dias" pode cair em outro mês
    LaunchedEffect(dia) {
        ano = partes(dia).ano
        mes = partes(dia).mes
    }

    fun mudarMes(delta: Int) {
        val total = ano * 12 + (mes - 1) + delta
        ano = total / 12
        mes = total % 12 + 1
    }

    val primeiro = diaDe(ano, mes, 1)
    val celulas: List<Int?> = List(diaDaSemana(primeiro)) { null } + (primeiro until primeiro + diasNoMes(ano, mes))
    val podeVoltar = ano * 12 + mes > atual.ano * 12 + atual.mes

    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Espaco.md), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { mudarMes(-1) }, enabled = podeVoltar) {
                    Icon(Icones.Anterior, contentDescription = "Mês anterior")
                }
                Text(
                    "${nomeMes(mes).capitalizar()} de $ano",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { mudarMes(1) }) { Icon(Icones.Proximo, contentDescription = "Próximo mês") }
            }
            Row {
                INICIAIS_SEMANA.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            celulas.chunked(7).forEach { semana ->
                Row {
                    semana.forEach { d ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                        ) {
                            if (d != null) {
                                val passado = d < diaHoje
                                val qualidade = qualidadeDia(pessoas, d, duracao)
                                val fundoDia = corFundo(qualidade.situacao)
                                val conteudoDia = corConteudo(qualidade.situacao)
                                val alguemTrabalha = pessoas.any { it.status(d) == StatusDia.TRABALHO }
                                val selecionado = d == dia
                                val forma = MaterialTheme.shapes.small
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(forma)
                                        .background(if (passado) Color.Transparent else fundoDia)
                                        .then(
                                            if (selecionado) {
                                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, forma)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable(
                                            enabled = !passado,
                                            role = Role.Button,
                                            onClickLabel = "Escolher ${diaPorExtenso(d)}: ${qualidade.rotulo}"
                                        ) { onEscolher(d) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            partes(d).dia.toString(),
                                            style = MaterialTheme.typography.bodyLarge.numerico,
                                            fontWeight = if (selecionado || d in feriados) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                passado -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                else -> conteudoDia
                                            }
                                        )
                                        // A maleta avisa que é dia de trabalho mesmo com o horário livre
                                        if (!passado && alguemTrabalha) {
                                            Icon(
                                                Icones.Trabalho,
                                                contentDescription = null,
                                                tint = conteudoDia,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                    if (!passado && d in feriados) {
                                        Icon(
                                            Icones.Estrela,
                                            contentDescription = "Feriado",
                                            tint = conteudoDia,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(9.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    repeat(7 - semana.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            FlowRow(
                Modifier.padding(top = Espaco.xs),
                horizontalArrangement = Arrangement.spacedBy(Espaco.md),
                verticalArrangement = Arrangement.spacedBy(Espaco.xs)
            ) {
                QualidadeDia.entries.forEach {
                    ItemLegendaEscolha(corFundo(it.situacao), corIndicador(it.situacao), it.rotulo)
                }
            }
        }
    }
}

@Composable
private fun ItemLegendaEscolha(fundo: Color, borda: Color, rotulo: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(fundo, CircleShape)
                .border(1.dp, borda, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Horário arrastável: a barra mostra quando todos estão livres no dia (verde).
 * Começa com uma alça só, a do início; o fim é opcional e ganha a segunda alça.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorHorario(
    dia: Int,
    pessoas: List<Pessoa>,
    inicio: Int,
    duracao: Int,
    escolhido: Boolean,
    onMudar: (Int, Int) -> Unit,
    onHorarioExato: () -> Unit
) {
    val semFim = duracao == SEM_FIM
    val fim = minOf(inicio + duracao, MINUTOS_DIA)
    val janela = Janela(inicio, duracaoReservada(duracao))
    val ocupadas = pessoas.filter { !it.livre(dia, janela) }
    val todosLivres = ocupadas.isEmpty()

    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Espaco.lg), verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            !escolhido -> "Escolha a hora"
                            semFim -> horarioDeMinutos(inicio)
                            else -> "${horarioDeMinutos(inicio)} – ${horarioDeMinutos(inicio + duracao)}"
                        },
                        style = MaterialTheme.typography.headlineSmall.numerico
                    )
                    Text(
                        when {
                            semFim -> "Sem hora para acabar"
                            duracao % 60 == 0 -> "${duracao / 60}h de duração"
                            else -> "${duracao / 60}h${duracao % 60} de duração"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onHorarioExato) { Text("Horário exato") }
            }

            // Disponibilidade do grupo no dia, alinhada com o controle deslizante
            BarraHorarios(
                ocupados = periodosOcupados(pessoas, dia),
                corLivre = corFundo(StatusDia.FOLGA),
                corOcupado = corFundo(StatusDia.TRABALHO),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                altura = 10.dp
            )
            if (semFim) {
                Slider(
                    value = inicio.toFloat(),
                    onValueChange = { onMudar(minOf((it / 30).roundToInt() * 30, MINUTOS_DIA - 30), SEM_FIM) },
                    valueRange = 0f..MINUTOS_DIA.toFloat(),
                    steps = MINUTOS_DIA / 30 - 1,
                    // Só o início importa aqui: a trilha pintada daria a ideia de um período
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                        activeTickColor = MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                RangeSlider(
                    value = inicio.toFloat()..fim.toFloat(),
                    onValueChange = { faixa ->
                        val a = (faixa.start / 30).roundToInt() * 30
                        val b = (faixa.endInclusive / 30).roundToInt() * 30
                        if (b - a >= 30) onMudar(a, b - a)
                    },
                    valueRange = 0f..MINUTOS_DIA.toFloat(),
                    steps = MINUTOS_DIA / 30 - 1
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("0h", "6h", "12h", "18h", "24h").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = {
                onMudar(inicio, if (semFim) minOf(120, MINUTOS_DIA - inicio) else SEM_FIM)
            }) { Text(if (semFim) "Definir fim (opcional)" else "Tirar o fim") }

            // Retorno imediato enquanto arrasta
            if (escolhido) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            corFundo(if (todosLivres) StatusDia.FOLGA else StatusDia.TRABALHO),
                            MaterialTheme.shapes.medium
                        )
                        .padding(Espaco.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (todosLivres) Icones.Confirmar else Icones.Fechar,
                        contentDescription = null,
                        tint = corIndicador(if (todosLivres) StatusDia.FOLGA else StatusDia.TRABALHO)
                    )
                    Spacer(Modifier.width(Espaco.md))
                    Text(
                        text = when {
                            todosLivres && pessoas.size <= 1 -> "Livre"
                            pessoas.size <= 1 -> when (pessoas[0].motivoOcupado(dia, janela)) {
                                TipoBloco.SONO -> "Sono"
                                TipoBloco.DESCANSO -> "Descanso"
                                else -> "Trabalho"
                            }
                            todosLivres -> "Todos livres"
                            else -> ocupadas.joinToString(", ") { it.nomeCurto } +
                                if (ocupadas.size == 1) " não está livre" else " não estão livres"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = corConteudo(if (todosLivres) StatusDia.FOLGA else StatusDia.TRABALHO)
                    )
                }
            }
        }
    }
}
