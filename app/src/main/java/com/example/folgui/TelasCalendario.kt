package com.example.folgui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SELECAO_GRUPO = -2

/** Quantas bolinhas de pessoas cabem na célula antes de virar contagem ("2/5"). */
private const val MAX_PONTOS = 4

// ============================================================================
// CALENDÁRIO: a escala de cada pessoa ou do grupo, em mês, semana, lista ou ano
// ============================================================================
/** Formas de ver a escala. A escolhida fica salva nas preferências. */
enum class VisaoCalendario(val rotulo: String, val icone: androidx.compose.ui.graphics.vector.ImageVector) {
    MES("Mês", Icones.Calendario),
    SEMANA("Semana", Icones.Colunas),
    LISTA("Lista", Icones.Lista),
    ANO("Ano", Icones.Grade);

    companion object {
        fun porNome(nome: String?): VisaoCalendario = entries.firstOrNull { it.name == nome } ?: MES
    }
}

@Composable
fun TelaCalendario(
    voce: Pessoa,
    pessoas: List<Pessoa>,
    eventos: List<Evento>,
    agenda: Map<Int, List<CompromissoAgenda>>,
    feriados: Map<Int, String>,
    cargaFeriados: Map<Int, Carga>,
    snackbar: SnackbarHostState,
    visao: VisaoCalendario,
    onMudarVisao: (VisaoCalendario) -> Unit,
    onCarregarAno: (Int) -> Unit,
    onAbrirDia: (Int) -> Unit,
    onEditarSono: () -> Unit
) {
    val atual = partes(hoje())
    var ano by rememberSaveable { mutableIntStateOf(atual.ano) }
    var mes by rememberSaveable { mutableIntStateOf(atual.mes) }
    var inicioSemana by rememberSaveable { mutableIntStateOf(hoje() - diaDaSemana(hoje())) }
    var selecao by rememberSaveable { mutableIntStateOf(if (pessoas.isEmpty()) Pessoa.ID_VOCE else SELECAO_GRUPO) }

    LaunchedEffect(ano) { onCarregarAno(ano) }
    LaunchedEffect(inicioSemana) { onCarregarAno(partes(inicioSemana).ano) }

    val todas = listOf(voce) + pessoas
    val modoGrupo = selecao == SELECAO_GRUPO && pessoas.isNotEmpty()
    // Se a pessoa selecionada foi removida, mostra a escala do usuário
    val pessoaVista = todas.firstOrNull { it.id == selecao } ?: voce
    val grupo = if (modoGrupo) todas else listOf(pessoaVista)

    fun voltarParaHoje() {
        ano = atual.ano
        mes = atual.mes
        inicioSemana = hoje() - diaDaSemana(hoje())
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quem aparece no calendário (sozinha, não há o que escolher)
            if (pessoas.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Espaco.lg),
                    horizontalArrangement = Arrangement.spacedBy(Espaco.sm)
                ) {
                    FilterChip(
                        selected = modoGrupo,
                        onClick = { selecao = SELECAO_GRUPO },
                        label = { Text("Todos juntos") },
                        leadingIcon = { Icon(Icones.Pessoas, contentDescription = null, Modifier.size(18.dp)) }
                    )
                    todas.forEach { p ->
                        FilterChip(
                            selected = !modoGrupo && pessoaVista.id == p.id,
                            onClick = { selecao = p.id },
                            label = { Text(p.nomeCurto, maxLines = 1) }
                        )
                    }
                }
            }

            Column(
                Modifier
                    .limitarLargura()
                    .padding(horizontal = Espaco.lg)
            ) {
                SeletorVisao(visao, onMudarVisao)

                when (visao) {
                    VisaoCalendario.MES -> VisaoMes(
                        todas = todas,
                        grupo = grupo,
                        modoGrupo = modoGrupo,
                        eventos = eventos,
                        agenda = agenda,
                        feriados = feriados,
                        cargaFeriados = cargaFeriados,
                        ano = ano,
                        mes = mes,
                        onMudarMes = { delta ->
                            val total = ano * 12 + (mes - 1) + delta
                            ano = total / 12
                            mes = total % 12 + 1
                        },
                        onHoje = ::voltarParaHoje,
                        onCarregarAno = onCarregarAno,
                        onAbrirDia = onAbrirDia
                    )
                    VisaoCalendario.SEMANA -> VisaoSemana(
                        grupo = grupo,
                        modoGrupo = modoGrupo,
                        todas = todas,
                        voce = voce,
                        eventos = eventos,
                        agenda = agenda,
                        feriados = feriados,
                        inicio = inicioSemana,
                        onMudar = { inicioSemana += it },
                        onHoje = ::voltarParaHoje,
                        onAbrirDia = onAbrirDia,
                        onEditarSono = onEditarSono
                    )
                    VisaoCalendario.LISTA -> VisaoLista(
                        grupo = grupo,
                        modoGrupo = modoGrupo,
                        todas = todas,
                        eventos = eventos,
                        agenda = agenda,
                        feriados = feriados,
                        onAbrirDia = onAbrirDia
                    )
                    VisaoCalendario.ANO -> VisaoAno(
                        grupo = grupo,
                        modoGrupo = modoGrupo,
                        ano = ano,
                        onMudarAno = { ano += it },
                        onHoje = ::voltarParaHoje,
                        onAbrirMes = {
                            mes = it
                            onMudarVisao(VisaoCalendario.MES)
                        }
                    )
                }
            }
        }
    }
}

/** Quatro visões em um trilho: a escolhida fica destacada, com ícone e nome curto. */
@Composable
private fun SeletorVisao(visao: VisaoCalendario, onMudar: (VisaoCalendario) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Espaco.xs)
            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            .padding(Espaco.xs)
    ) {
        VisaoCalendario.entries.forEach { v ->
            val marcada = v == visao
            val cor = if (marcada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (marcada) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
                    .clickable(role = Role.Tab) { onMudar(v) }
                    .padding(vertical = Espaco.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(v.icone, contentDescription = null, tint = cor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Espaco.xs))
                Text(v.rotulo, style = MaterialTheme.typography.labelMedium, color = cor, maxLines = 1)
            }
        }
    }
}

/** Título do período com as setas e o atalho para voltar a hoje. */
@Composable
fun NavegacaoPeriodo(
    titulo: String,
    onAnterior: () -> Unit,
    onProximo: () -> Unit,
    mostrarHoje: Boolean,
    onHoje: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Espaco.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAnterior) { Icon(Icones.Anterior, contentDescription = "Anterior") }
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        if (mostrarHoje) TextButton(onClick = onHoje) { Text("Hoje") }
        IconButton(onClick = onProximo) { Icon(Icones.Proximo, contentDescription = "Próximo") }
    }
}

/** Grade do mês. Sozinha, cada dia mostra o turno ou "Folga"; em grupo, quem está livre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisaoMes(
    todas: List<Pessoa>,
    grupo: List<Pessoa>,
    modoGrupo: Boolean,
    eventos: List<Evento>,
    agenda: Map<Int, List<CompromissoAgenda>>,
    feriados: Map<Int, String>,
    cargaFeriados: Map<Int, Carga>,
    ano: Int,
    mes: Int,
    onMudarMes: (Int) -> Unit,
    onHoje: () -> Unit,
    onCarregarAno: (Int) -> Unit,
    onAbrirDia: (Int) -> Unit
) {
    val atual = partes(hoje())
    val pessoaVista = grupo.first()
    val primeiroDia = diaDe(ano, mes, 1)
    val diasDoMes = (primeiroDia until primeiroDia + diasNoMes(ano, mes)).toList()
    val diasComCompromisso = eventos.map { it.dia }.toSet() + agenda.keys
    val feriadosDoMes = diasDoMes.mapNotNull { d -> feriados[d]?.let { d to it } }

    if (modoGrupo) {
        MelhoresDias(
            pessoas = todas,
            dias = remember(todas) { melhoresDias(todas) },
            duracao = 60,
            selecionado = null,
            onEscolher = onAbrirDia,
            modifier = Modifier.padding(bottom = Espaco.md)
        )
    }

    NavegacaoPeriodo(
        titulo = "${nomeMes(mes).capitalizar()} de $ano",
        onAnterior = { onMudarMes(-1) },
        onProximo = { onMudarMes(1) },
        mostrarHoje = ano != atual.ano || mes != atual.mes,
        onHoje = onHoje
    )

    Row(Modifier.fillMaxWidth()) {
        INICIAIS_SEMANA.forEach { inicial ->
            Text(
                text = inicial,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(Modifier.height(Espaco.xs))

    // Grade do mês: espaços vazios antes do dia 1 para alinhar com o dia da semana
    val celulas: List<Int?> = List(partes(primeiroDia).diaSemana) { null } + diasDoMes
    celulas.chunked(7).forEach { semana ->
        Row(Modifier.fillMaxWidth()) {
            semana.forEach { dia ->
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(if (modoGrupo) 1f else 0.82f)
                        .padding(2.dp)
                ) {
                    if (dia != null) {
                        if (modoGrupo) {
                            val qualidade = qualidadeDia(todas, dia)
                            CelulaDia(
                                dia = dia,
                                fundo = corFundo(qualidade.situacao),
                                conteudo = corConteudo(qualidade.situacao),
                                descricao = qualidade.rotulo,
                                legenda = null,
                                feriado = dia in feriados,
                                temCompromisso = dia in diasComCompromisso,
                                livres = todas.map { it.status(dia).livre },
                                noturno = false,
                                mudanca = todas.any { it.excecao(dia) != null },
                                onClick = { onAbrirDia(dia) }
                            )
                        } else {
                            val status = pessoaVista.status(dia)
                            val turno = pessoaVista.turnosNoDia(dia).firstOrNull()
                            CelulaDia(
                                dia = dia,
                                fundo = corFundo(status),
                                conteudo = corConteudo(status),
                                descricao = pessoaVista.descricaoDia(dia),
                                legenda = turno?.compacto ?: status.rotulo,
                                feriado = dia in feriados,
                                temCompromisso = dia in diasComCompromisso,
                                livres = null,
                                noturno = turno?.noturno == true,
                                mudanca = pessoaVista.excecao(dia) != null,
                                onClick = { onAbrirDia(dia) }
                            )
                        }
                    }
                }
            }
            repeat(7 - semana.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    // Legenda logo abaixo da grade
    Spacer(Modifier.height(Espaco.sm))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Espaco.md),
        verticalArrangement = Arrangement.spacedBy(Espaco.xs)
    ) {
        if (modoGrupo) {
            QualidadeDia.entries.forEach { ItemLegenda(corFundo(it.situacao), corIndicador(it.situacao), it.rotulo) }
        } else {
            listOf(StatusDia.TRABALHO, StatusDia.FOLGA).forEach { ItemLegenda(corFundo(it), corIndicador(it), it.rotulo) }
            if (diasDoMes.any { pessoaVista.status(it) == StatusDia.FERIAS }) {
                ItemLegenda(corFundo(StatusDia.FERIAS), corIndicador(StatusDia.FERIAS), StatusDia.FERIAS.rotulo)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icones.Estrela, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(Espaco.xs))
            TextoLegenda("Feriado")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarcaCompromisso()
            Spacer(Modifier.width(Espaco.xs))
            TextoLegenda("Evento")
        }
    }

    // Resumo do mês por categoria
    Spacer(Modifier.height(Espaco.md))
    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
        if (modoGrupo) {
            QualidadeDia.entries.forEach { q ->
                Contador(
                    valor = diasDoMes.count { qualidadeDia(todas, it) == q },
                    rotulo = q.rotulo,
                    fundo = corFundo(q.situacao),
                    conteudo = corConteudo(q.situacao),
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            listOf(StatusDia.TRABALHO, StatusDia.FOLGA, StatusDia.FERIAS).forEach { st ->
                Contador(
                    valor = diasDoMes.count { pessoaVista.status(it) == st },
                    rotulo = st.rotulo,
                    fundo = corFundo(st),
                    conteudo = corConteudo(st),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Feriados nacionais do mês (BrasilAPI): só aparece quando há o que mostrar
    if (cargaFeriados[ano] == Carga.ERRO) {
        TextButton(onClick = { onCarregarAno(ano) }, modifier = Modifier.padding(top = Espaco.sm)) {
            Text("Recarregar feriados")
        }
    }
    if (feriadosDoMes.isNotEmpty()) {
        Spacer(Modifier.height(Espaco.md))
        Cartao(Modifier.fillMaxWidth()) {
            Column {
                feriadosDoMes.forEachIndexed { i, (dia, nome) ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAbrirDia(dia) }
                            .padding(horizontal = Espaco.lg, vertical = Espaco.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icones.Estrela, contentDescription = null, tint = corAmbar(), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Espaco.md))
                        Text(nome, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(Espaco.sm))
                        Text(
                            diaResumido(dia),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(Espaco.xl))
}

/**
 * Célula do dia. No modo grupo, cada bolinha é uma pessoa:
 * cheia = livre, vazada = trabalha (a primeira é sempre você).
 */
@Composable
private fun CelulaDia(
    dia: Int,
    fundo: Color,
    conteudo: Color,
    descricao: String,
    legenda: String?,
    feriado: Boolean,
    temCompromisso: Boolean,
    livres: List<Boolean>?,
    noturno: Boolean,
    mudanca: Boolean,
    onClick: () -> Unit
) {
    val ehHoje = dia == hoje()
    val passado = dia < hoje()
    val forma = MaterialTheme.shapes.small
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(forma)
            .background(if (passado) fundo.copy(alpha = 0.55f) else fundo)
            .then(if (ehHoje) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, forma) else Modifier)
            .clickable(onClickLabel = "Ver ${diaPorExtenso(dia)}: $descricao", onClick = onClick)
    ) {
        if (temCompromisso) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
            ) { MarcaCompromisso() }
        }
        if (feriado) {
            Icon(
                Icones.Estrela,
                contentDescription = "Feriado",
                tint = conteudo,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(10.dp)
            )
        }
        if (mudanca) {
            Icon(
                Icones.Editar,
                contentDescription = "Mudança na escala",
                tint = conteudo,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(10.dp)
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = partes(dia).dia.toString(),
                style = MaterialTheme.typography.bodyLarge.numerico,
                fontWeight = if (ehHoje || feriado) FontWeight.Bold else FontWeight.Normal,
                color = conteudo
            )
            if (legenda != null) {
                Text(
                    text = legenda,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                    color = conteudo,
                    maxLines = 1
                )
            }
            if (noturno) {
                Icon(Icones.Lua, contentDescription = "Plantão noturno", tint = conteudo, modifier = Modifier.size(10.dp))
            }
            if (livres != null) {
                if (livres.size <= MAX_PONTOS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        livres.forEach { PontoPessoa(livre = it, cor = conteudo) }
                    }
                } else {
                    Text(
                        text = "${livres.count { it }}/${livres.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = conteudo
                    )
                }
            }
        }
    }
}

@Composable
private fun PontoPessoa(livre: Boolean, cor: Color) {
    Box(
        Modifier
            .size(6.dp)
            .then(
                if (livre) {
                    Modifier.background(cor, CircleShape)
                } else {
                    Modifier.border(1.dp, cor.copy(alpha = 0.7f), CircleShape)
                }
            )
    )
}

@Composable
private fun MarcaCompromisso() {
    Box(
        Modifier
            .size(6.dp)
            .background(MaterialTheme.colorScheme.secondary, CircleShape)
    )
}

@Composable
fun TextoLegenda(texto: String) {
    Text(texto, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ItemLegenda(fundo: Color, borda: Color, rotulo: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .background(fundo, MaterialTheme.shapes.extraSmall)
                .border(1.dp, borda.copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall)
        )
        Spacer(Modifier.width(6.dp))
        TextoLegenda(rotulo)
    }
}

@Composable
fun Contador(valor: Int, rotulo: String, fundo: Color, conteudo: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(fundo, MaterialTheme.shapes.medium)
            .padding(vertical = Espaco.sm, horizontal = Espaco.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(valor.toString(), style = MaterialTheme.typography.titleLarge.numerico, color = conteudo)
        Text(
            rotulo,
            style = MaterialTheme.typography.labelMedium,
            color = conteudo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================================
// DETALHE DO DIA: quem trabalha, quem folga, eventos e compromissos da agenda
// ============================================================================
@Composable
fun TelaDia(
    diaInicial: Int,
    todas: List<Pessoa>,
    eventos: List<Evento>,
    agenda: Map<Int, List<CompromissoAgenda>>,
    feriados: Map<Int, String>,
    snackbar: SnackbarHostState,
    onCarregarAno: (Int) -> Unit,
    onVoltar: () -> Unit,
    onAbrirEvento: (Int) -> Unit,
    onNovoEvento: (Int) -> Unit,
    onMudarDia: (Pessoa, Int, Excecao?) -> Unit
) {
    var dia by rememberSaveable { mutableIntStateOf(diaInicial) }
    var editando by rememberSaveable { mutableStateOf<Int?>(null) }
    val ano = partes(dia).ano
    LaunchedEffect(ano) { onCarregarAno(ano) }

    val eventosDoDia = eventos.filter { it.dia == dia }
    // Eventos do Folgui já enviados para a agenda não aparecem duas vezes
    val compromissos = agenda[dia].orEmpty().filter { c -> eventosDoDia.none { it.titulo == c.titulo } }
    val feriado = feriados[dia]
    val passado = dia < hoje()

    Scaffold(
        topBar = {
            BarraSuperior(titulo = rotuloRelativo(dia), onVoltar = onVoltar) {
                IconButton(onClick = { dia -= 1 }) { Icon(Icones.Anterior, contentDescription = "Dia anterior") }
                IconButton(onClick = { dia += 1 }) { Icon(Icones.Proximo, contentDescription = "Próximo dia") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!passado) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = { onNovoEvento(dia) },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = Espaco.lg, vertical = Espaco.md)
                            .limitarLargura()
                            .heightIn(min = 52.dp)
                    ) {
                        Icon(Icones.Adicionar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Espaco.sm))
                        Text("Novo evento")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.limitarLargura()) {
                Column(Modifier.padding(horizontal = Espaco.lg, vertical = Espaco.sm)) {
                    Text(diaPorExtenso(dia), style = MaterialTheme.typography.titleMedium)
                    if (feriado != null) {
                        Spacer(Modifier.height(Espaco.sm))
                        val voce = todas.first { it.ehVoce }
                        if (voce.perguntarFeriado(dia)) {
                            PerguntaFeriado(dia, feriado, { d, trabalha -> onMudarDia(voce, d, voce.respostaFeriado(d, trabalha)) })
                        } else {
                            SeloFeriado(feriado)
                        }
                    }
                }

                CartaoSituacao(
                    pessoas = todas,
                    dia = dia,
                    modifier = Modifier.padding(horizontal = Espaco.lg, vertical = Espaco.sm)
                )

                LinhaDoTempo(
                    pessoas = todas,
                    dia = dia,
                    modifier = Modifier.padding(horizontal = Espaco.lg, vertical = Espaco.sm)
                )

                TituloSecao("Mudar dia", modifier = Modifier.padding(top = Espaco.sm))
                Cartao(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Espaco.lg)
                ) {
                    Column {
                        todas.forEachIndexed { i, pessoa ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            LinhaPessoa(
                                pessoa = pessoa,
                                dia = dia,
                                detalhe = pessoa.descricaoDia(dia),
                                onClick = { editando = pessoa.id }
                            )
                        }
                    }
                }

                if (eventosDoDia.isNotEmpty()) {
                    TituloSecao("Eventos", modifier = Modifier.padding(top = Espaco.md))
                    Cartao(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Espaco.lg)
                    ) {
                        Column {
                            eventosDoDia.forEachIndexed { i, evento ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                LinhaEvento(evento = evento, todas = todas, onClick = { onAbrirEvento(evento.id) })
                            }
                        }
                    }
                }

                if (compromissos.isNotEmpty()) {
                    TituloSecao("Google Agenda", modifier = Modifier.padding(top = Espaco.md))
                    Cartao(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Espaco.lg)
                    ) {
                        Column {
                            compromissos.forEachIndexed { i, c ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                LinhaCompromisso(c)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Espaco.xl))
            }
        }
    }

    todas.firstOrNull { it.id == editando }?.let { pessoa ->
        DialogoMudarDia(
            pessoa = pessoa,
            dia = dia,
            onConfirmar = { excecao ->
                editando = null
                onMudarDia(pessoa, dia, excecao)
            },
            onFechar = { editando = null }
        )
    }
}

/**
 * Mudança pontual na escala: em dia de trabalho, folga/atestado/outro horário;
 * em dia de folga, trabalho extra. Também volta à escala normal.
 */
@Composable
private fun DialogoMudarDia(
    pessoa: Pessoa,
    dia: Int,
    onConfirmar: (Excecao?) -> Unit,
    onFechar: () -> Unit
) {
    val context = LocalContext.current
    val pelaEscala = pessoa.turnoPelaEscala(dia)
    val atual = pessoa.excecao(dia)
    val opcoes = if (pelaEscala != null) {
        listOf(TipoExcecao.FOLGA, TipoExcecao.AUSENCIA, TipoExcecao.HORARIO)
    } else {
        listOf(TipoExcecao.TRABALHO)
    }
    val turnoBase = atual?.turno ?: pelaEscala ?: pessoa.turnos.firstOrNull() ?: pessoa.escala.turnoPadrao
    var escolhido by rememberSaveable { mutableStateOf((atual?.tipo ?: opcoes.first()).codigo) }
    var inicio by rememberSaveable { mutableIntStateOf(turnoBase.inicio) }
    var fim by rememberSaveable { mutableIntStateOf(turnoBase.fim) }
    val tipo = TipoExcecao.porCodigo(escolhido) ?: opcoes.first()

    if (pessoa.emFerias(dia)) {
        AlertDialog(
            onDismissRequest = onFechar,
            title = { Text("${pessoa.nomeCurto} está de férias") },
            text = { Text("Edite as férias para mudar.") },
            confirmButton = { TextButton(onClick = onFechar) { Text("Entendi") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("${pessoa.nomeCurto} · ${diaResumido(dia)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                Text(
                    text = "Pela escala: " + (pelaEscala?.frase?.lowercase() ?: "folga"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                opcoes.forEach { opcao ->
                    val marcado = opcao == tipo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (marcado) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (marcado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.medium
                            )
                            .clickable(role = Role.RadioButton) { escolhido = opcao.codigo }
                            .padding(Espaco.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (opcao) {
                                TipoExcecao.FOLGA -> Icones.DiaLivre
                                TipoExcecao.AUSENCIA -> Icones.Aviso
                                else -> Icones.Trabalho
                            },
                            contentDescription = null,
                            tint = if (marcado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(Espaco.md))
                        Column(Modifier.weight(1f)) {
                            Text(opcao.rotulo, style = MaterialTheme.typography.titleSmall)
                            Text(
                                when (opcao) {
                                    TipoExcecao.FOLGA -> "Ou troca de plantão"
                                    TipoExcecao.AUSENCIA -> "Não vai trabalhar"
                                    TipoExcecao.HORARIO -> "Trabalha em outro horário"
                                    TipoExcecao.TRABALHO -> "Hora extra ou troca"
                                    TipoExcecao.FERIADO -> "Trabalha no feriado"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (tipo.trabalha) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                        CampoSelecao(
                            valor = horarioDeMinutos(inicio),
                            rotulo = "Entrada",
                            icone = Icones.Relogio,
                            onClick = { abrirSeletorHorario(context, horarioDeMinutos(inicio)) { inicio = minutosDoHorario(it) } },
                            modifier = Modifier.weight(1f)
                        )
                        CampoSelecao(
                            valor = horarioDeMinutos(fim),
                            rotulo = "Saída",
                            icone = Icones.Relogio,
                            onClick = { abrirSeletorHorario(context, horarioDeMinutos(fim)) { fim = minutosDoHorario(it) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (atual != null) {
                    TextButton(onClick = { onConfirmar(null) }) {
                        Icon(Icones.Sincronizar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Espaco.sm))
                        Text("Voltar à escala")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmar(Excecao(dia, tipo, if (tipo.trabalha) Turno(inicio, fim) else null))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )
}

@Composable
fun LinhaCompromisso(compromisso: CompromissoAgenda, dia: Int? = null) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Abrir na Google Agenda para editar") {
                if (!IntegracaoGoogle.abrirCompromisso(context, compromisso)) {
                    Toast.makeText(context, "Instale a Google Agenda para editar.", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(horizontal = Espaco.lg, vertical = Espaco.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dia != null) {
            SeloData(dia)
        } else {
            Icon(Icones.Calendario, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.width(Espaco.lg))
        Column(Modifier.weight(1f)) {
            Text(
                compromisso.titulo,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(compromisso.horario, compromisso.agenda).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
