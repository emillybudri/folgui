package com.example.folgui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ALTURA_HORA = 20.dp
private val LARGURA_HORAS = 26.dp

/** Dias que a lista mostra de cada vez. */
private const val DIAS_LISTA = 30

/** Cor e nome do dia para uma pessoa ou para o grupo. */
private data class AparenciaDia(val fundo: Color, val conteudo: Color, val titulo: String)

@Composable
private fun aparencia(chave: Any): AparenciaDia = when (chave) {
    is StatusDia -> AparenciaDia(
        corFundo(chave),
        corConteudo(chave),
        if (chave == StatusDia.TRABALHO) "Trabalho" else chave.rotulo
    )
    is QualidadeDia -> AparenciaDia(corFundo(chave.situacao), corConteudo(chave.situacao), chave.rotulo)
    else -> error("Situação desconhecida")
}

private fun chaveDia(grupo: List<Pessoa>, modoGrupo: Boolean, dia: Int): Any =
    if (modoGrupo) qualidadeDia(grupo, dia) else grupo.first().status(dia)

private fun eventosDaPessoa(grupo: List<Pessoa>, modoGrupo: Boolean, eventos: List<Evento>, todas: List<Pessoa>) =
    eventos.filter { e -> modoGrupo || pessoasDoEvento(e, todas).any { it.id == grupo.first().id } }

// ============================================================================
// SEMANA: as 24 horas de cada dia, com trabalho, sono, eventos e horário livre
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisaoSemana(
    grupo: List<Pessoa>,
    modoGrupo: Boolean,
    todas: List<Pessoa>,
    voce: Pessoa,
    eventos: List<Evento>,
    agenda: Map<Int, List<CompromissoAgenda>>,
    feriados: Map<Int, String>,
    inicio: Int,
    onMudar: (Int) -> Unit,
    onHoje: () -> Unit,
    onAbrirDia: (Int) -> Unit,
    onEditarSono: () -> Unit
) {
    val dias = (inicio until inicio + 7).toList()
    val visiveis = eventosDaPessoa(grupo, modoGrupo, eventos, todas)
    val diaHoje = hoje()

    NavegacaoPeriodo(
        titulo = "${partes(inicio).dia} ${mesCurto(inicio)} a ${partes(inicio + 6).dia} ${mesCurto(inicio + 6)}",
        onAnterior = { onMudar(-7) },
        onProximo = { onMudar(7) },
        mostrarHoje = diaHoje !in dias,
        onHoje = onHoje
    )

    // Cabeçalho com a situação de cada dia (todos da mesma altura, com ou sem feriado)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Spacer(Modifier.width(LARGURA_HORAS))
        dias.forEach { d ->
            val a = aparencia(chaveDia(grupo, modoGrupo, d))
            val forma = MaterialTheme.shapes.small
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 1.dp)
                    .clip(forma)
                    .background(a.fundo)
                    .then(if (d == diaHoje) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, forma) else Modifier)
                    .clickable(onClickLabel = "Ver ${diaPorExtenso(d)}: ${a.titulo}") { onAbrirDia(d) }
                    .padding(vertical = Espaco.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(diaSemanaCurto(d), style = MaterialTheme.typography.labelSmall, color = a.conteudo)
                Text(
                    partes(d).dia.toString(),
                    style = MaterialTheme.typography.titleSmall.numerico,
                    color = a.conteudo,
                    fontWeight = if (d in feriados) FontWeight.Bold else FontWeight.Normal
                )
                if (d in feriados) {
                    Icon(Icones.Estrela, contentDescription = "Feriado", tint = a.conteudo, modifier = Modifier.size(10.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(Espaco.xs))

    Row(Modifier.fillMaxWidth()) {
        // Régua de horas
        Box(
            Modifier
                .width(LARGURA_HORAS)
                .height(ALTURA_HORA * 24)
        ) {
            (0..21 step 3).forEach { h ->
                Text(
                    "${h}h",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(y = ALTURA_HORA * h)
                )
            }
        }
        dias.forEach { d ->
            ColunaDia(
                dia = d,
                grupo = grupo,
                eventos = visiveis.filter { it.dia == d },
                compromissos = agenda[d].orEmpty(),
                onClick = { onAbrirDia(d) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(Modifier.height(Espaco.md))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Espaco.lg),
        verticalArrangement = Arrangement.spacedBy(Espaco.xs)
    ) {
        ItemLegenda(corFundo(StatusDia.FOLGA), corIndicador(StatusDia.FOLGA), if (modoGrupo) "Todos livres" else "Livre")
        ItemLegenda(corFundo(StatusDia.TRABALHO), corIndicador(StatusDia.TRABALHO), "Trabalho")
        ItemLegenda(corSonoFundo(), corSono(), "Sono")
        ItemLegenda(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary, "Evento")
    }

    Spacer(Modifier.height(Espaco.md))
    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
        if (modoGrupo) {
            Contador(
                valor = dias.count { qualidadeDia(grupo, it) == QualidadeDia.MELHOR },
                rotulo = "Melhores dias",
                fundo = corFundo(StatusDia.FOLGA),
                conteudo = corConteudo(StatusDia.FOLGA),
                modifier = Modifier.weight(1f)
            )
        } else {
            val pessoa = grupo.first()
            val trabalho = dias.count { pessoa.status(it) == StatusDia.TRABALHO }
            Contador(trabalho, "Trabalho", corFundo(StatusDia.TRABALHO), corConteudo(StatusDia.TRABALHO), Modifier.weight(1f))
            Contador(7 - trabalho, "Folga", corFundo(StatusDia.FOLGA), corConteudo(StatusDia.FOLGA), Modifier.weight(1f))
            Contador(
                dias.sumOf { d -> pessoa.periodosNoDia(d).sumOf { (a, b) -> b - a } } / 60,
                "Horas",
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f)
            )
        }
    }

    Spacer(Modifier.height(Espaco.sm))
    Cartao(Modifier.fillMaxWidth(), onClick = onEditarSono) {
        Row(Modifier.padding(Espaco.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icones.Lua, contentDescription = null, tint = corSono())
            Spacer(Modifier.width(Espaco.md))
            Text(
                voce.sono?.let { "Sono ${it.resumo}" } ?: "Definir sono",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(Icones.Editar, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(Espaco.xl))
}

@Composable
private fun ColunaDia(
    dia: Int,
    grupo: List<Pessoa>,
    eventos: List<Evento>,
    compromissos: List<CompromissoAgenda>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ferias = grupo.all { it.status(dia) == StatusDia.FERIAS }
    val fundo = if (ferias) corFundo(StatusDia.FERIAS) else corFundo(StatusDia.FOLGA).copy(alpha = 0.45f)
    val corTrabalho = corFundo(StatusDia.TRABALHO)
    val sono = mesclarPeriodos(grupo.flatMap { it.sonoNoDia(dia) })
    val descanso = mesclarPeriodos(grupo.flatMap { it.descansoNoDia(dia) })
    val trabalho = mesclarPeriodos(grupo.flatMap { it.periodosNoDia(dia) })

    Box(
        modifier
            .height(ALTURA_HORA * 24)
            .padding(horizontal = 1.dp)
            .clip(MaterialTheme.shapes.small)
            .background(fundo)
            .clickable(onClickLabel = "Ver ${diaPorExtenso(dia)}", onClick = onClick)
    ) {
        sono.forEach { (a, b) -> Trecho(a, b, corSonoFundo(), "Sono".takeIf { b - a >= 120 }, corSonoConteudo()) }
        descanso.forEach { (a, b) -> Trecho(a, b, corTrabalho.copy(alpha = 0.5f)) }
        trabalho.forEach { (a, b) ->
            Trecho(a, b, corTrabalho, "${horaCurta(a)}–${horaCurta(b)}".takeIf { b - a >= 120 }, corConteudo(StatusDia.TRABALHO))
        }
        compromissos.filter { c -> !c.diaInteiro && eventos.none { it.titulo == c.titulo } }.forEach { c ->
            val a = if (diaDoMomento(c.inicio) == dia) minutosDoHorario(horarioDoMomento(c.inicio)) else 0
            val b = if (diaDoMomento(c.fim) == dia) minutosDoHorario(horarioDoMomento(c.fim)) else MINUTOS_DIA
            Trecho(a, maxOf(b, a + 30), MaterialTheme.colorScheme.secondary, c.titulo, MaterialTheme.colorScheme.onSecondary)
        }
        eventos.forEach { e ->
            val a = e.janela.inicio
            val b = minOf(e.janela.fim, MINUTOS_DIA)
            Trecho(a, b, MaterialTheme.colorScheme.secondary, e.titulo, MaterialTheme.colorScheme.onSecondary)
        }
        if (dia == hoje()) {
            Box(
                Modifier
                    .offset(y = ALTURA_HORA * (minutoDoDiaAtual() / 60f))
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

/** Trecho do dia na coluna da semana, na altura proporcional às horas. */
@Composable
private fun Trecho(inicio: Int, fim: Int, cor: Color, texto: String? = null, corTexto: Color = Color.Unspecified) {
    Box(
        Modifier
            .offset(y = ALTURA_HORA * (inicio / 60f))
            .fillMaxWidth()
            .height(ALTURA_HORA * ((fim - inicio) / 60f))
            .padding(1.dp)
            .background(cor, MaterialTheme.shapes.extraSmall)
    ) {
        if (texto != null) {
            Text(
                texto,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                color = corTexto,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
            )
        }
    }
}

// ============================================================================
// LISTA: os próximos dias em sequência, agrupados em trabalho e folga
// ============================================================================
@Composable
fun VisaoLista(
    grupo: List<Pessoa>,
    modoGrupo: Boolean,
    todas: List<Pessoa>,
    eventos: List<Evento>,
    agenda: Map<Int, List<CompromissoAgenda>>,
    feriados: Map<Int, String>,
    onAbrirDia: (Int) -> Unit
) {
    var quantidade by rememberSaveable { mutableIntStateOf(DIAS_LISTA) }
    val diaHoje = hoje()
    val visiveis = eventosDaPessoa(grupo, modoGrupo, eventos, todas)

    Spacer(Modifier.height(Espaco.md))
    Cartao(Modifier.fillMaxWidth()) {
        Column {
            (diaHoje until diaHoje + quantidade).forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val a = aparencia(chaveDia(grupo, modoGrupo, d))
                val pessoa = grupo.first()
                LinhaDiaLista(
                    dia = d,
                    aparencia = a,
                    detalhe = if (modoGrupo) a.titulo else listOfNotNull(
                        pessoa.turnosNoDia(d).firstOrNull()?.resumo ?: a.titulo,
                        pessoa.excecao(d)?.tipo?.rotulo
                    ).joinToString(" · "),
                    feriado = feriados[d],
                    eventos = visiveis.filter { it.dia == d }.map { "${it.horario} ${it.titulo}" } +
                        agenda[d].orEmpty()
                            .filter { c -> visiveis.none { it.dia == d && it.titulo == c.titulo } }
                            .map { "${it.horario} ${it.titulo}" },
                    onClick = { onAbrirDia(d) }
                )
            }
        }
    }
    TextButton(
        onClick = { quantidade += DIAS_LISTA },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Espaco.sm)
    ) { Text("Ver mais") }
}

@Composable
private fun LinhaDiaLista(
    dia: Int,
    aparencia: AparenciaDia,
    detalhe: String,
    feriado: String?,
    eventos: List<String>,
    onClick: () -> Unit
) {
    val ehHoje = dia == hoje()
    val forma = MaterialTheme.shapes.small
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Ver dia", onClick = onClick)
            .padding(horizontal = Espaco.md, vertical = Espaco.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .width(48.dp)
                .clip(forma)
                .background(aparencia.fundo)
                .then(if (ehHoje) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, forma) else Modifier)
                .padding(vertical = Espaco.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(diaSemanaCurto(dia), style = MaterialTheme.typography.labelSmall, color = aparencia.conteudo)
            Text(partes(dia).dia.toString(), style = MaterialTheme.typography.titleMedium.numerico, color = aparencia.conteudo)
        }
        Spacer(Modifier.width(Espaco.md))
        Column(Modifier.weight(1f)) {
            Text(detalhe, style = MaterialTheme.typography.bodyMedium)
            if (feriado != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icones.Estrela, contentDescription = null, tint = corAmbar(), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(Espaco.xs))
                    Text(feriado, style = MaterialTheme.typography.labelMedium, color = corAmbar(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            eventos.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================================
// ANO: os 12 meses de uma vez, para ver o ritmo da escala e as férias
// ============================================================================
@Composable
fun VisaoAno(
    grupo: List<Pessoa>,
    modoGrupo: Boolean,
    ano: Int,
    onMudarAno: (Int) -> Unit,
    onHoje: () -> Unit,
    onAbrirMes: (Int) -> Unit
) {
    val atual = partes(hoje())
    val primeiro = diaDe(ano, 1, 1)
    val diasDoAno = (primeiro until diaDe(ano + 1, 1, 1)).toList()

    NavegacaoPeriodo(
        titulo = ano.toString(),
        onAnterior = { onMudarAno(-1) },
        onProximo = { onMudarAno(1) },
        mostrarHoje = ano != atual.ano,
        onHoje = onHoje
    )

    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
        val chaves: List<Any> = if (modoGrupo) SituacaoGrupo.entries else listOf(StatusDia.TRABALHO, StatusDia.FOLGA, StatusDia.FERIAS)
        chaves.forEach { chave ->
            val a = aparencia(chave)
            Contador(
                valor = diasDoAno.count { chaveDia(grupo, modoGrupo, it) == chave },
                rotulo = a.titulo,
                fundo = a.fundo,
                conteudo = a.conteudo,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(Modifier.height(Espaco.md))

    (1..12).chunked(3).forEach { trio ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espaco.md)) {
            trio.forEach { mes ->
                MiniMes(
                    ano = ano,
                    mes = mes,
                    grupo = grupo,
                    modoGrupo = modoGrupo,
                    onClick = { onAbrirMes(mes) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(Espaco.md))
    }
    Spacer(Modifier.height(Espaco.xl))
}

@Composable
private fun MiniMes(
    ano: Int,
    mes: Int,
    grupo: List<Pessoa>,
    modoGrupo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primeiro = diaDe(ano, mes, 1)
    val celulas: List<Int?> = List(diaDaSemana(primeiro)) { null } + (primeiro until primeiro + diasNoMes(ano, mes))
    val diaHoje = hoje()
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Abrir ${nomeMes(mes)}", onClick = onClick)
            .padding(Espaco.xs)
    ) {
        Text(nomeMes(mes).capitalizar(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        celulas.chunked(7).forEach { semana ->
            Row {
                semana.forEach { d ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp)
                    ) {
                        if (d != null) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(aparencia(chaveDia(grupo, modoGrupo, d)).fundo, CircleShape)
                                    .then(if (d == diaHoje) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            )
                        }
                    }
                }
                repeat(7 - semana.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
