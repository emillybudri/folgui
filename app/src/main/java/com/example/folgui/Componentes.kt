package com.example.folgui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

fun Modifier.limitarLargura() = this
    .widthIn(max = Espaco.larguraMaxima)
    .fillMaxWidth()

// ============================================================================
// Barras e seções
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(
    titulo: String,
    onVoltar: (() -> Unit)? = null,
    iconeVoltar: ImageVector = Icones.Voltar,
    acoes: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onVoltar != null) {
                IconButton(onClick = onVoltar) {
                    Icon(iconeVoltar, contentDescription = "Voltar")
                }
            }
        },
        actions = acoes,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun TituloSecao(
    texto: String,
    modifier: Modifier = Modifier,
    acao: String? = null,
    onAcao: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Espaco.alvoToque)
            .padding(horizontal = Espaco.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (acao != null) {
            TextButton(onClick = onAcao) { Text(acao) }
        }
    }
}

@Composable
fun Cartao(
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    elevado: Boolean = true,
    onClick: (() -> Unit)? = null,
    conteudo: @Composable () -> Unit
) {
    val forma = MaterialTheme.shapes.large
    // Sem borda: um cartão branco se separa do fundo por uma sombra leve
    Surface(
        color = cor,
        shape = forma,
        shadowElevation = if (elevado) 1.dp else 0.dp,
        modifier = modifier
            .clip(forma)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        conteudo()
    }
}

@Composable
fun EstadoVazio(
    icone: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    acao: String? = null,
    imagem: Int? = null,
    onAcao: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Espaco.xl, vertical = Espaco.xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (imagem != null) {
                Image(
                    painter = painterResource(imagem),
                    contentDescription = null,
                    modifier = Modifier
                        .width(220.dp)
                        .aspectRatio(1.5f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.height(Espaco.lg))
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Espaco.xs))
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )
            if (acao != null) {
                Spacer(Modifier.height(Espaco.lg))
                FilledTonalButton(onClick = onAcao) {
                    Icon(Icones.Adicionar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Espaco.sm))
                    Text(acao)
                }
            }
        }
    }
}

// ============================================================================
// Pessoas e situação do dia
// ============================================================================
@Composable
fun Avatar(
    nome: String,
    destaque: Boolean = false,
    tamanho: Dp = 40.dp,
    cores: Pair<Color, Color>? = null
) {
    val iniciais = nome.trim().split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    val (fundo, conteudo) = cores ?: if (destaque) {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = Modifier
            .size(tamanho)
            .background(fundo, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (tamanho < 32.dp) iniciais.take(1) else iniciais,
            style = when {
                tamanho >= 48.dp -> MaterialTheme.typography.titleMedium
                tamanho < 32.dp -> MaterialTheme.typography.labelSmall
                else -> MaterialTheme.typography.labelLarge
            },
            color = conteudo
        )
    }
}

@Composable
fun SeloStatus(status: StatusDia, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(corFundo(status), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(corIndicador(status), CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = status.rotulo,
            style = MaterialTheme.typography.labelMedium,
            color = corConteudo(status)
        )
    }
}

/**
 * Cartão que responde "dá para todo mundo nesse dia?" com cor, ícone e texto:
 * todos livres (verde), parte livre (âmbar) ou ninguém livre (vermelho).
 */
@Composable
fun CartaoSituacao(
    pessoas: List<Pessoa>,
    dia: Int,
    modifier: Modifier = Modifier,
    janela: Janela? = null,
    complemento: String? = null,
    extra: @Composable () -> Unit = {}
) {
    val situacao = situacaoGrupo(pessoas, dia, janela)
    // Sozinha e sem horário escolhido, o título é o próprio dia: "Trabalho", "Folga", "Férias"
    val titulo = if (pessoas.size == 1 && janela == null) {
        pessoas[0].status(dia).let { if (it == StatusDia.TRABALHO) "Trabalho" else it.rotulo }
    } else {
        resumoDisponibilidade(pessoas, dia, janela).second
    }
    val ocupados = pessoas.filter { !it.livre(dia, janela) }
    val livres = pessoas.filter { it.livre(dia, janela) }.map { it.nomeCurto }
    val detalhe = if (janela != null) {
        // Horário do evento: mostra o turno de quem estará trabalhando
        if (ocupados.isEmpty()) {
            if (pessoas.size <= 1) {
                "Fora do trabalho e do sono"
            } else {
                "Sem turnos nesse horário"
            }
        } else {
            ocupados.joinToString("\n") { p ->
                when (p.motivoOcupado(dia, janela)) {
                    TipoBloco.SONO -> "${p.nomeCurto}: sono, ${p.sono?.resumo}"
                    TipoBloco.DESCANSO -> "${p.nomeCurto}: descanso"
                    else -> "${p.nomeCurto}: ${p.turnosNoDia(dia).joinToString(", ") { it.resumo }.ifEmpty { p.resumoTurno }}"
                }
            }
        }
    } else {
        when {
            pessoas.size <= 1 ->
                pessoas.first().turnosNoDia(dia).joinToString(", ") { it.resumo }.ifEmpty { "Dia livre" }
            situacao == SituacaoGrupo.TODOS_LIVRES -> motivoLivres(pessoas, dia)
            situacao == SituacaoGrupo.PARCIAL ->
                "Livre: ${livres.joinToString(", ")} · Trabalha: ${ocupados.joinToString(", ") { it.nomeCurto }}"
            else -> "Todos trabalham"
        }
    }
    // Mesmo em dia de trabalho, pode sobrar um horário em que todos estão livres
    val juntos = if (janela == null && situacao != SituacaoGrupo.TODOS_LIVRES) {
        janelasLivres(pessoas, dia).takeIf { it.isNotEmpty() }?.let { lista ->
            (if (pessoas.size <= 1) "Livre " else "Juntos ") +
                lista.joinToString(", ") { "${horaCurta(it.inicio)} às ${horaCurta(it.fim)}" }
        }
    } else {
        null
    }

    Surface(
        color = corFundo(situacao),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Espaco.lg), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = iconeSituacao(situacao),
                    contentDescription = null,
                    tint = corIndicador(situacao),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(Espaco.md))
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = corConteudo(situacao).takeIf { situacao != SituacaoGrupo.NINGUEM_LIVRE }
                        ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = detalhe,
                style = MaterialTheme.typography.bodyMedium,
                color = corConteudo(situacao),
                modifier = Modifier.padding(start = 22.dp + Espaco.md)
            )
            if (juntos != null) {
                Row(
                    modifier = Modifier.padding(start = 22.dp + Espaco.md, top = Espaco.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icones.Relogio,
                        contentDescription = null,
                        tint = corConteudo(situacao),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Espaco.xs))
                    Text(
                        text = juntos,
                        style = MaterialTheme.typography.labelLarge,
                        color = corConteudo(situacao)
                    )
                }
            }
            if (complemento != null) {
                Text(
                    text = complemento,
                    style = MaterialTheme.typography.bodySmall,
                    color = corConteudo(situacao),
                    modifier = Modifier.padding(start = 22.dp + Espaco.md)
                )
            }
            extra()
        }
    }
}

/** Linha com avatar, nome, escala e a situação da pessoa no dia informado. */
@Composable
fun LinhaPessoa(
    pessoa: Pessoa,
    dia: Int,
    modifier: Modifier = Modifier,
    detalhe: String = pessoa.resumoEscala,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Espaco.lg, vertical = Espaco.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(pessoa.nome, cores = coresPessoa(pessoa))
        Spacer(Modifier.width(Espaco.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = pessoa.nome,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detalhe,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(Espaco.sm))
        SeloStatus(pessoa.status(dia))
    }
}

/** Dia em destaque com o mês abaixo, para percorrer listas pela data. */
@Composable
fun SeloData(dia: Int, destaque: Boolean = false) {
    val p = partes(dia)
    Column(
        modifier = Modifier
            .width(52.dp)
            .background(
                if (destaque) corFundo(StatusDia.FOLGA) else MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.shapes.medium
            )
            .padding(vertical = Espaco.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = diaSemanaCurto(dia).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (destaque) corConteudo(StatusDia.FOLGA) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = p.dia.toString(),
            style = MaterialTheme.typography.titleLarge.numerico,
            color = if (destaque) corConteudo(StatusDia.FOLGA) else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = mesCurto(dia),
            style = MaterialTheme.typography.labelSmall,
            color = if (destaque) corConteudo(StatusDia.FOLGA) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Faixa de dias consecutivos coloridos pela situação (prévia da escala). */
@Composable
fun FaixaDias(
    inicio: Int,
    quantidade: Int,
    status: (Int) -> StatusDia,
    modifier: Modifier = Modifier,
    marcado: Int? = null,
    turnoDoDia: ((Int) -> Turno?)? = null,
    onToque: ((Int) -> Unit)? = null
) {
    val diaHoje = hoje()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
        (0 until quantidade).chunked(7).forEach { semana ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                semana.forEach { deslocamento ->
                    val dia = inicio + deslocamento
                    val st = status(dia)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = INICIAIS_SEMANA[partes(dia).diaSemana],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Espaco.xs))
                        val forma = MaterialTheme.shapes.small
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(forma)
                                .background(corFundo(st))
                                .then(
                                    when {
                                        dia == marcado -> Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, forma)
                                        dia == diaHoje -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, forma)
                                        else -> Modifier
                                    }
                                )
                                .then(
                                    if (onToque != null) {
                                        Modifier.clickable(
                                            role = Role.Button,
                                            onClickLabel = "Escolher ${diaPorExtenso(dia)}"
                                        ) { onToque(dia) }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = partes(dia).dia.toString(),
                                    style = MaterialTheme.typography.labelLarge.numerico,
                                    color = corConteudo(st)
                                )
                                val turno = turnoDoDia?.invoke(dia)
                                if (turno != null && st == StatusDia.TRABALHO) {
                                    Text(
                                        text = turno.compacto,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = corConteudo(st),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
                // Mantém o tamanho das células quando a última semana fica incompleta
                repeat(7 - semana.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun Legenda(modifier: Modifier = Modifier, comFerias: Boolean = true) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Espaco.lg)) {
        listOf(StatusDia.TRABALHO, StatusDia.FOLGA, StatusDia.FERIAS)
            .filter { comFerias || it != StatusDia.FERIAS }
            .forEach { st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(corFundo(st), MaterialTheme.shapes.extraSmall)
                            .border(1.dp, corIndicador(st).copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = st.rotulo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
    }
}

// ============================================================================
// Campos de formulário
// ============================================================================
@Composable
fun CampoTexto(
    valor: String,
    onValorChange: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    erro: String? = null,
    apoio: String? = null,
    placeholder: String? = null,
    icone: ImageVector? = null,
    capitalizacao: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    acaoTeclado: ImeAction = ImeAction.Next,
    senha: Boolean = false,
    onConcluir: () -> Unit = {}
) {
    val textoApoio = erro ?: apoio
    val foco = LocalFocusManager.current
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        label = { Text(rotulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = icone?.let { { Icon(it, contentDescription = null) } },
        isError = erro != null,
        supportingText = textoApoio?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalizacao,
            keyboardType = tipoTeclado,
            imeAction = acaoTeclado
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                foco.clearFocus()
                onConcluir()
            },
            onSend = { onConcluir() }
        ),
        visualTransformation = if (senha) PasswordVisualTransformation() else VisualTransformation.None,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    )
}

/** Campo somente leitura que abre um seletor (data ou horário) ao ser tocado. */
@Composable
fun CampoSelecao(
    valor: String,
    rotulo: String,
    icone: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    erro: String? = null,
    apoio: String? = null,
    placeholder: String? = null
) {
    val textoApoio = erro ?: apoio
    Box(modifier) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            label = { Text(rotulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = placeholder?.let { { Text(it) } },
            leadingIcon = { Icon(icone, contentDescription = null) },
            isError = erro != null,
            supportingText = textoApoio?.let { { Text(it) } },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        )
        // Camada transparente: o toque abre o seletor em vez do teclado
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClickLabel = rotulo, onClick = onClick)
        )
    }
}

fun abrirSeletorData(context: Context, diaInicial: Int, onEscolher: (Int) -> Unit) {
    val p = partes(diaInicial)
    DatePickerDialog(
        context,
        { _, ano, mes, diaMes -> onEscolher(diaDe(ano, mes + 1, diaMes)) },
        p.ano,
        p.mes - 1,
        p.dia
    ).show()
}

fun abrirSeletorHorario(context: Context, horario: String, onEscolher: (String) -> Unit) {
    val agora = Calendar.getInstance()
    val partesHora = horario.split(":")
    val hora = partesHora.getOrNull(0)?.toIntOrNull() ?: agora.get(Calendar.HOUR_OF_DAY)
    val minuto = partesHora.getOrNull(1)?.toIntOrNull() ?: 0
    TimePickerDialog(
        context,
        { _, h, m -> onEscolher(horarioDe(h, m)) },
        hora,
        minuto,
        true
    ).show()
}

// ============================================================================
// Linha do tempo: as 24 horas do dia de cada pessoa
// ============================================================================

/** Barra de 0h a 24h com os trechos ocupados pintados. */
@Composable
fun BarraHorarios(
    ocupados: List<Pair<Int, Int>>,
    corLivre: Color,
    corOcupado: Color,
    modifier: Modifier = Modifier,
    altura: Dp = 14.dp,
    descanso: List<Pair<Int, Int>> = emptyList(),
    corDescanso: Color = corOcupado.copy(alpha = 0.4f),
    sono: List<Pair<Int, Int>> = emptyList(),
    corDoSono: Color = corSono()
) {
    val forma = MaterialTheme.shapes.extraSmall
    Box(
        modifier
            .height(altura)
            .clip(forma)
            .background(corLivre)
    ) {
        // O trabalho é pintado por último: num plantão, ele aparece por cima do horário de sono
        if (sono.isNotEmpty()) TrechosPintados(sono, corDoSono)
        if (descanso.isNotEmpty()) TrechosPintados(descanso, corDescanso)
        TrechosPintados(ocupados, corOcupado)
    }
}

@Composable
private fun TrechosPintados(trechos: List<Pair<Int, Int>>, cor: Color) {
    Row(Modifier.fillMaxSize()) {
        var cursor = 0
        mesclarPeriodos(trechos).forEach { (a, b) ->
            if (a > cursor) Spacer(Modifier.weight((a - cursor).toFloat()))
            if (b > a) {
                Box(
                    Modifier
                        .weight((b - a).toFloat())
                        .fillMaxHeight()
                        .background(cor)
                )
            }
            cursor = b
        }
        if (cursor < MINUTOS_DIA) Spacer(Modifier.weight((MINUTOS_DIA - cursor).toFloat()))
    }
}

/** Faixa posicionada num trecho do dia (usada para o horário do evento e para o "agora"). */
@Composable
private fun MarcaTrecho(inicio: Int, fim: Int, modifier: Modifier) {
    Row(Modifier.fillMaxSize()) {
        if (inicio > 0) Spacer(Modifier.weight(inicio.toFloat()))
        Box(
            modifier
                .weight((fim - inicio).coerceAtLeast(1).toFloat())
                .fillMaxHeight()
        )
        if (fim < MINUTOS_DIA) Spacer(Modifier.weight((MINUTOS_DIA - fim).toFloat()))
    }
}

/**
 * Linha do tempo do dia: uma barra por pessoa e, no fim, a barra "Juntos"
 * com os horários em que todos estão livres. [destaque] marca o horário de um evento.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinhaDoTempo(
    pessoas: List<Pessoa>,
    dia: Int,
    modifier: Modifier = Modifier,
    destaque: Janela? = null,
    titulo: String = "Hora a hora"
) {
    val larguraRotulo = 84.dp
    val corLivre = corFundo(StatusDia.FOLGA)
    val corOcupado = corIndicador(StatusDia.TRABALHO)
    val corFerias = MaterialTheme.colorScheme.tertiaryContainer
    val juntos = janelasLivres(pessoas, dia)

    Cartao(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Espaco.lg), verticalArrangement = Arrangement.spacedBy(Espaco.md)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)

            Box {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Spacer(Modifier.width(larguraRotulo))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("0h", "6h", "12h", "18h", "24h").forEach {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    pessoas.forEach { pessoa ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.width(larguraRotulo), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(pessoa.nome, tamanho = 24.dp, cores = coresPessoa(pessoa))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    pessoa.nomeCurto,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = Espaco.xs)
                                )
                            }
                            val ferias = pessoa.status(dia) == StatusDia.FERIAS
                            BarraHorarios(
                                ocupados = pessoa.periodosNoDia(dia),
                                corLivre = if (ferias) corFerias else corLivre,
                                corOcupado = corOcupado,
                                modifier = Modifier.weight(1f),
                                descanso = pessoa.descansoNoDia(dia),
                                sono = pessoa.sonoNoDia(dia)
                            )
                        }
                    }
                    if (pessoas.size > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.width(larguraRotulo), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .background(corIndicador(StatusDia.FOLGA), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icones.Pessoas,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("Juntos", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            BarraHorarios(
                                ocupados = periodosOcupados(pessoas, dia),
                                corLivre = corIndicador(StatusDia.FOLGA),
                                corOcupado = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.weight(1f),
                                altura = 18.dp
                            )
                        }
                    }
                }

                // Marcações por cima das barras: horário do evento e a hora atual
                Row(Modifier.matchParentSize()) {
                    Spacer(Modifier.width(larguraRotulo))
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (destaque != null) {
                            MarcaTrecho(
                                inicio = destaque.inicio.coerceIn(0, MINUTOS_DIA),
                                fim = destaque.fim.coerceIn(0, MINUTOS_DIA),
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        MaterialTheme.shapes.extraSmall
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
                            )
                        }
                        if (dia == hoje()) {
                            val agora = minutoDoDiaAtual()
                            MarcaTrecho(
                                inicio = agora,
                                fim = minOf(agora + 6, MINUTOS_DIA),
                                modifier = Modifier.background(MaterialTheme.colorScheme.onSurface, CircleShape)
                            )
                        }
                    }
                }
            }

            // Legenda e resumo em texto
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Espaco.lg),
                verticalArrangement = Arrangement.spacedBy(Espaco.xs)
            ) {
                ItemLegendaBarra(corLivre, "Livre")
                ItemLegendaBarra(corOcupado, "Trabalho")
                if (pessoas.any { it.descansoNoDia(dia).isNotEmpty() }) {
                    ItemLegendaBarra(corOcupado.copy(alpha = 0.4f), "Descanso")
                }
                if (pessoas.any { it.sonoNoDia(dia).isNotEmpty() }) ItemLegendaBarra(corSono(), "Sono")
                if (pessoas.any { it.status(dia) == StatusDia.FERIAS }) ItemLegendaBarra(corFerias, "Férias")
                if (destaque != null) ItemLegendaBarra(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), "Evento")
                if (dia == hoje()) ItemLegendaBarra(MaterialTheme.colorScheme.onSurface, "Agora")
            }
            if (pessoas.size > 1 && juntos.isNotEmpty()) {
                Text(
                    text = "Juntos: " + juntos.joinToString(", ") { "${horaCurta(it.inicio)} às ${horaCurta(it.fim)}" },
                    style = MaterialTheme.typography.labelLarge,
                    color = corIndicador(StatusDia.FOLGA)
                )
            }
        }
    }
}

@Composable
private fun ItemLegendaBarra(cor: Color, rotulo: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 16.dp, height = 8.dp)
                .background(cor, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
