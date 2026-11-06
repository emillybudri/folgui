package com.example.folgui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Quantos dias a tira de círculos da tela inicial mostra. */
private const val DIAS_TIRA = 14

// ============================================================================
// HOJE: a resposta em um olhar, depois os próximos dias e quem está por perto
// ============================================================================
@Composable
fun TelaInicio(
    voce: Pessoa,
    pessoas: List<Pessoa>,
    eventos: List<Evento>,
    feriados: Map<Int, String>,
    snackbar: SnackbarHostState,
    onResponderFeriado: (Int, Boolean) -> Unit,
    onAbrirDia: (Int) -> Unit,
    onAbrirEvento: (Int) -> Unit,
    onNovaPessoa: () -> Unit,
    onNovoEvento: (Int?) -> Unit,
    onPlanejar: (Encontro) -> Unit,
    onQuandoDa: () -> Unit,
    onVerPessoas: () -> Unit,
    onEditarSono: () -> Unit
) {
    val diaHoje = hoje()
    val todas = listOf(voce) + pessoas
    val folgas = diasEmComum(todas, diaHoje + 1, quantidade = 8)
    val proximoEvento = eventos.firstOrNull { !it.jaPassou }
    // Melhor próximo horário com todo mundo livre (pelo menos 2 horas)
    val encontro = remember(todas) {
        if (pessoas.isEmpty()) null else proximosEncontros(todas, duracao = 120, quantidade = 1).firstOrNull()
    }
    val feriadoSemResposta = feriados.keys.sorted()
        .firstOrNull { it <= diaHoje + 45 && voce.perguntarFeriado(it, diaHoje) }
    val margem = Modifier
        .limitarLargura()
        .padding(horizontal = Espaco.lg)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = Espaco.xs, bottom = Espaco.xl),
            verticalArrangement = Arrangement.spacedBy(Espaco.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "hoje") {
                CartaoHoje(voce, feriados[diaHoje], { onAbrirDia(diaHoje) }, margem)
            }

            if (feriadoSemResposta != null) {
                item(key = "feriado") {
                    PerguntaFeriado(feriadoSemResposta, feriados.getValue(feriadoSemResposta), onResponderFeriado, margem)
                }
            }

            item(key = "tira") {
                TiraDias(voce, eventos, feriados, onAbrirDia)
            }

            item(key = "acoes") {
                Row(margem, horizontalArrangement = Arrangement.SpaceEvenly) {
                    AcaoRapida(Icones.Relogio, "Quando dá?", onClick = onQuandoDa)
                    AcaoRapida(Icones.Lua, "Sono", destaque = voce.sono == null, onClick = onEditarSono)
                    AcaoRapida(Icones.Editar, "Mudar dia") { onAbrirDia(diaHoje) }
                }
            }

            if (encontro != null) {
                item(key = "encontro") { CartaoEncontro(encontro, { onPlanejar(encontro) }, margem) }
            }

            // Sozinha, esta seção só repetiria o cartão de cima
            if (pessoas.isNotEmpty()) {
                item(key = "pessoas") {
                    Column(verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                        TituloCurto("Agora", Modifier.limitarLargura())
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Espaco.lg),
                            horizontalArrangement = Arrangement.spacedBy(Espaco.md)
                        ) {
                            items(pessoas, key = { it.id }) { PessoaCirculo(it, onVerPessoas) }
                            item(key = "nova") { NovaPessoaCirculo(onNovaPessoa) }
                        }
                    }
                }
            }

            item(key = "linha_tempo") {
                LinhaDoTempo(pessoas = todas, dia = diaHoje, titulo = "Hoje", modifier = margem)
            }

            if (folgas.isNotEmpty()) {
                item(key = "folgas") {
                    Column(verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                        TituloCurto(if (pessoas.isEmpty()) "Próximas folgas" else "Folgas juntos", Modifier.limitarLargura())
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Espaco.lg),
                            horizontalArrangement = Arrangement.spacedBy(Espaco.sm)
                        ) {
                            items(folgas) { d -> FolgaCartao(d, feriados[d] != null) { onAbrirDia(d) } }
                        }
                    }
                }
            }

            item(key = "evento") {
                Column(verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                    TituloCurto("Próximo evento", Modifier.limitarLargura())
                    Cartao(margem) {
                        if (proximoEvento == null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onNovoEvento(folgas.firstOrNull()) }
                                    .padding(Espaco.lg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icones.Adicionar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(Espaco.md))
                                Text("Criar evento", style = MaterialTheme.typography.titleSmall)
                            }
                        } else {
                            LinhaEvento(proximoEvento, todas, { onAbrirEvento(proximoEvento.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TituloCurto(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = Espaco.lg)
    )
}

/** A resposta principal do app: o que é hoje. */
@Composable
private fun CartaoHoje(voce: Pessoa, feriado: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val diaHoje = hoje()
    val status = voce.status(diaHoje)
    val turnos = voce.turnosNoDia(diaHoje)
    val turno = turnos.firstOrNull()
    val tinta = corConteudo(status)
    val agora = voce.situacaoAgora().second.substringAfter("· ").capitalizar()
    val proximo = when (status) {
        StatusDia.FERIAS -> voce.feriasFim?.let { "Até ${diaResumido(it)}" }
        StatusDia.FOLGA -> voce.proximoDia(diaHoje + 1, livre = false)?.let { "Trabalho ${rotuloRelativo(it).lowercase()}" }
        StatusDia.TRABALHO -> voce.proximoDia(diaHoje + 1, livre = true)?.let { "Folga ${rotuloRelativo(it).lowercase()}" }
    }

    Cartao(modifier = modifier.fillMaxWidth(), cor = corFundo(status), elevado = false, onClick = onClick) {
        Column(Modifier.padding(Espaco.xl), verticalArrangement = Arrangement.spacedBy(Espaco.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        diaResumido(diaHoje).capitalizar(),
                        style = MaterialTheme.typography.labelLarge,
                        color = tinta
                    )
                    Text(
                        if (status == StatusDia.TRABALHO) "Trabalho" else status.rotulo,
                        style = MaterialTheme.typography.displaySmall,
                        color = tinta
                    )
                    if (turnos.isNotEmpty()) {
                        Text(turnos.joinToString(" · ") { it.resumo }, style = MaterialTheme.typography.titleMedium, color = tinta)
                    }
                }
                Box(
                    Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            status == StatusDia.FERIAS -> Icones.Sol
                            status.livre -> Icones.DiaLivre
                            turno?.noturno == true -> Icones.Lua
                            else -> Icones.Trabalho
                        },
                        contentDescription = null,
                        tint = corIndicador(status),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                PilulaHoje(agora)
                if (proximo != null) PilulaHoje(proximo)
            }
            if (feriado != null) SeloFeriado(feriado)
        }
    }
}

@Composable
private fun PilulaHoje(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
            .padding(horizontal = Espaco.md, vertical = 6.dp)
    )
}

/** Próximos dias em círculos, como stories: a cor já diz se é trabalho ou folga. */
@Composable
private fun TiraDias(voce: Pessoa, eventos: List<Evento>, feriados: Map<Int, String>, onAbrirDia: (Int) -> Unit) {
    val diaHoje = hoje()
    val comEvento = eventos.map { it.dia }.toSet()
    LazyRow(
        contentPadding = PaddingValues(horizontal = Espaco.lg),
        horizontalArrangement = Arrangement.spacedBy(Espaco.sm)
    ) {
        items((diaHoje until diaHoje + DIAS_TIRA).toList()) { d ->
            val status = voce.status(d)
            Column(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "Ver ${diaPorExtenso(d)}: ${status.rotulo}") { onAbrirDia(d) }
                    .padding(vertical = Espaco.xs, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    diaSemanaCurto(d),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == diaHoje) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Espaco.xs))
                Box(
                    Modifier
                        .size(44.dp)
                        .background(corFundo(status), CircleShape)
                        .then(if (d == diaHoje) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        partes(d).dia.toString(),
                        style = MaterialTheme.typography.titleSmall.numerico,
                        color = corConteudo(status)
                    )
                }
                Spacer(Modifier.height(Espaco.xs))
                Box(
                    Modifier
                        .size(5.dp)
                        .background(
                            when {
                                d in comEvento -> MaterialTheme.colorScheme.primary
                                d in feriados -> corAmbar()
                                else -> Color.Transparent
                            },
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun AcaoRapida(icone: ImageVector, rotulo: String, destaque: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .width(76.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = Espaco.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            if (destaque) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .background(corAmbar(), CircleShape)
                )
            }
        }
        Spacer(Modifier.height(Espaco.xs))
        Text(
            rotulo,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/** O próximo horário em que todos podem se encontrar, pronto para virar evento. */
@Composable
private fun CartaoEncontro(encontro: Encontro, onPlanejar: () -> Unit, modifier: Modifier = Modifier) {
    Cartao(modifier.fillMaxWidth(), onClick = onPlanejar) {
        Row(Modifier.padding(Espaco.lg), verticalAlignment = Alignment.CenterVertically) {
            SeloData(encontro.dia, destaque = true)
            Spacer(Modifier.width(Espaco.md))
            Column(Modifier.weight(1f)) {
                Text("Todos livres", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${horaCurta(encontro.janela.inicio)} às ${horaCurta(encontro.janela.fim)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icones.Adicionar, contentDescription = "Planejar", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Pessoa em círculo: a cor do anel e uma palavra dizem o que ela está fazendo agora. */
@Composable
private fun PessoaCirculo(pessoa: Pessoa, onClick: () -> Unit) {
    val motivo = pessoa.motivoOcupado(hoje(), Janela(minutoDoDiaAtual(), 1))
    val cor = when (motivo) {
        null -> corIndicador(StatusDia.FOLGA)
        TipoBloco.SONO -> corSono()
        else -> corIndicador(StatusDia.TRABALHO)
    }
    Column(
        Modifier
            .width(68.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .border(2.5.dp, cor, CircleShape)
                .padding(4.dp)
        ) {
            Avatar(pessoa.nome, tamanho = 48.dp, cores = coresPessoa(pessoa))
        }
        Spacer(Modifier.height(Espaco.xs))
        Text(
            pessoa.nome.substringBefore(" "),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            when (motivo) {
                null -> "Livre"
                TipoBloco.TRABALHO -> "Trabalho"
                TipoBloco.DESCANSO -> "Descanso"
                TipoBloco.SONO -> "Sono"
            },
            style = MaterialTheme.typography.labelSmall,
            color = cor
        )
    }
}

@Composable
private fun NovaPessoaCirculo(onClick: () -> Unit) {
    Column(
        Modifier
            .width(68.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = "Adicionar pessoa", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(61.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icones.Adicionar, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Espaco.xs))
        Text("Adicionar", style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun FolgaCartao(dia: Int, feriado: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(corFundo(StatusDia.FOLGA))
            .clickable(onClick = onClick)
            .padding(vertical = Espaco.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val tinta = corConteudo(StatusDia.FOLGA)
        Text(diaSemanaCurto(dia), style = MaterialTheme.typography.labelMedium, color = tinta)
        Text(partes(dia).dia.toString(), style = MaterialTheme.typography.titleLarge.numerico, color = tinta)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mesCurto(dia), style = MaterialTheme.typography.labelSmall, color = tinta)
            if (feriado) {
                Spacer(Modifier.width(2.dp))
                Icon(Icones.Estrela, contentDescription = "Feriado", tint = tinta, modifier = Modifier.size(10.dp))
            }
        }
    }
}

/** Pergunta se a pessoa trabalha no feriado, porque a escala sozinha não tem como saber. */
@Composable
fun PerguntaFeriado(dia: Int, nome: String, onResponder: (Int, Boolean) -> Unit, modifier: Modifier = Modifier) {
    Cartao(modifier.fillMaxWidth(), cor = corAmbarFundo(), elevado = false) {
        Column(Modifier.padding(Espaco.md), verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icones.Estrela, contentDescription = null, tint = corAmbar(), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Espaco.sm))
                Text(
                    "$nome · ${diaResumido(dia)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = corAmbarConteudo(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("Você trabalhará neste dia?", style = MaterialTheme.typography.titleMedium, color = corAmbarConteudo())
            Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                FilledTonalButton(onClick = { onResponder(dia, true) }, modifier = Modifier.weight(1f)) { Text("Sim") }
                FilledTonalButton(onClick = { onResponder(dia, false) }, modifier = Modifier.weight(1f)) { Text("Não") }
            }
        }
    }
}

/** Atalhos para os melhores dias de um encontro, com a cor de cada um. */
@Composable
fun MelhoresDias(
    pessoas: List<Pessoa>,
    dias: List<Int>,
    duracao: Int,
    selecionado: Int?,
    onEscolher: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
        Text(
            if (pessoas.size > 1) "Melhores dias para vocês se encontrarem" else "Melhores dias",
            style = MaterialTheme.typography.titleSmall
        )
        if (dias.isEmpty()) {
            Text(
                "Sem horário juntos nos próximos 2 meses",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
            items(dias, key = { it }) { d ->
                val situacao = qualidadeDia(pessoas, d, duracao).situacao
                FilterChip(
                    selected = d == selecionado,
                    onClick = { onEscolher(d) },
                    label = { Text(diaResumido(d), maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = corFundo(situacao),
                        labelColor = corConteudo(situacao)
                    )
                )
            }
        }
    }
}

@Composable
fun SeloFeriado(nome: String) {
    Row(
        modifier = Modifier
            .background(corAmbarFundo(), CircleShape)
            .padding(horizontal = Espaco.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icones.Estrela, contentDescription = null, tint = corAmbar(), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(Espaco.xs))
        Text(
            text = nome,
            style = MaterialTheme.typography.labelMedium,
            color = corAmbarConteudo(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LinhaEvento(evento: Evento, todas: List<Pessoa>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val quemVai = pessoasDoEvento(evento, todas)
    val (livres, resumo) = resumoDisponibilidade(quemVai, evento.dia, evento.janela)
    val lembreteAtivo = evento.momentoLembrete?.let { it > System.currentTimeMillis() } == true

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Abrir evento", onClick = onClick)
            .padding(horizontal = Espaco.lg, vertical = Espaco.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeloData(evento.dia)
        Spacer(Modifier.width(Espaco.md))
        Column(Modifier.weight(1f)) {
            Text(
                evento.titulo,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(evento.faixaHorario, evento.local).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Só fala da disponibilidade quando há algo errado
            if (!evento.jaPassou && !livres) {
                Text(
                    text = resumo,
                    style = MaterialTheme.typography.labelMedium,
                    color = corIndicador(StatusDia.TRABALHO),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (lembreteAtivo) {
            Spacer(Modifier.width(Espaco.sm))
            Icon(
                Icones.Sino,
                contentDescription = "Lembrete ativo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ============================================================================
// PESSOAS: o usuário (DataStore) e as pessoas cadastradas (SQLite)
// ============================================================================
@Composable
fun TelaPessoas(
    voce: Pessoa,
    pessoas: List<Pessoa>,
    snackbar: SnackbarHostState,
    onAbrirPerfil: () -> Unit,
    onAbrirPessoa: (Int) -> Unit,
    onNovaPessoa: () -> Unit
) {
    val diaHoje = hoje()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNovaPessoa,
                icon = { Icon(Icones.Adicionar, contentDescription = null) },
                text = { Text("Pessoa") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = Espaco.xs, bottom = Espaco.folgaFab),
            verticalArrangement = Arrangement.spacedBy(Espaco.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "voce") {
                Cartao(
                    modifier = Modifier
                        .limitarLargura()
                        .padding(horizontal = Espaco.lg)
                ) {
                    LinhaPessoa(pessoa = voce, dia = diaHoje, detalhe = voce.resumoEscala, onClick = onAbrirPerfil)
                }
            }

            if (pessoas.isEmpty()) {
                item(key = "vazio") {
                    EstadoVazio(
                        icone = Icones.Pessoas,
                        titulo = "Ninguém ainda",
                        imagem = R.drawable.ilustracao_encontro,
                        texto = "Adicione alguém e veja as folgas juntos.",
                        modifier = Modifier.limitarLargura()
                    )
                }
            } else {
                items(pessoas, key = { it.id }) { pessoa ->
                    Cartao(
                        modifier = Modifier
                            .limitarLargura()
                            .padding(horizontal = Espaco.lg)
                            .animateItem()
                    ) {
                        LinhaPessoa(
                            pessoa = pessoa,
                            dia = diaHoje,
                            detalhe = pessoa.resumoEscala,
                            onClick = { onAbrirPessoa(pessoa.id) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// EVENTOS: shows, passeios e viagens marcados (SQLite + AlarmManager)
// ============================================================================
@Composable
fun TelaEventos(
    eventos: List<Evento>,
    todas: List<Pessoa>,
    agenda: Map<Int, List<CompromissoAgenda>>,
    agendaConectada: Boolean,
    snackbar: SnackbarHostState,
    onAbrirEvento: (Int) -> Unit,
    onConectarAgenda: () -> Unit,
    onDesconectarAgenda: () -> Unit
) {
    val proximos = eventos.filter { !it.jaPassou }
    val anteriores = eventos.filter { it.jaPassou }.reversed()
    val diaHoje = hoje()
    // Próximos compromissos da agenda (sem repetir os eventos criados no Folgui)
    val compromissos = agenda.keys.filter { it >= diaHoje }.sorted()
        .flatMap { d -> agenda[d].orEmpty().map { d to it } }
        .filter { (d, c) -> c.fim > System.currentTimeMillis() && eventos.none { it.dia == d && it.titulo == c.titulo } }
        .distinctBy { (_, c) -> c.titulo + c.inicio }
        .take(5)
    val margem = Modifier
        .limitarLargura()
        .padding(horizontal = Espaco.lg)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = Espaco.xs, bottom = Espaco.xl),
            verticalArrangement = Arrangement.spacedBy(Espaco.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (eventos.isEmpty()) {
                item(key = "vazio") {
                    EstadoVazio(
                        icone = Icones.Evento,
                        titulo = "Sem eventos",
                        imagem = R.drawable.ilustracao_calendario,
                        texto = "Toque no + para criar.",
                        modifier = Modifier.limitarLargura()
                    )
                }
            }
            if (proximos.isNotEmpty()) {
                item(key = "proximos") { TituloCurto("Próximos", Modifier.limitarLargura()) }
                items(proximos, key = { it.id }) { evento ->
                    Cartao(margem.animateItem()) { LinhaEvento(evento, todas, { onAbrirEvento(evento.id) }) }
                }
            }
            if (agendaConectada && compromissos.isNotEmpty()) {
                item(key = "agenda_titulo") {
                    TituloCurto(
                        "Google Agenda",
                        Modifier
                            .limitarLargura()
                            .padding(top = Espaco.sm)
                    )
                }
                item(key = "agenda_lista") {
                    Cartao(margem) {
                        Column {
                            compromissos.forEachIndexed { i, (d, c) ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                LinhaCompromisso(c, dia = d)
                            }
                        }
                    }
                }
            }
            if (anteriores.isNotEmpty()) {
                item(key = "anteriores") {
                    TituloCurto(
                        "Anteriores",
                        Modifier
                            .limitarLargura()
                            .padding(top = Espaco.sm)
                    )
                }
                items(anteriores, key = { it.id }) { evento ->
                    Cartao(margem.animateItem().alpha(0.7f)) { LinhaEvento(evento, todas, { onAbrirEvento(evento.id) }) }
                }
            }
            item(key = "agenda") {
                Row(
                    margem.padding(top = Espaco.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (agendaConectada) Icones.Confirmar else Icones.Calendario,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Espaco.sm))
                    Text(
                        "Google Agenda",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (agendaConectada) {
                        TextButton(onClick = onDesconectarAgenda) { Text("Desconectar") }
                    } else {
                        FilledTonalButton(onClick = onConectarAgenda) { Text("Conectar") }
                    }
                }
            }
        }
    }
}
