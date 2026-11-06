package com.example.folgui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

// ============================================================================
// Prévia da lista de compromissos, dentro da conversa com a IA. Tudo o que a pessoa
// pode dizer no chat também dá para fazer aqui, tocando.
// ============================================================================

@Composable
private fun Rotulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Espaco.sm))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartaoPlano(
    plano: PlanoLote,
    analises: Map<Int, AnaliseItem>,
    grupos: List<Grupo>,
    criando: Boolean,
    agendaLiberada: Boolean,
    agendaGoogle: Boolean,
    onMudar: (PlanoLote?) -> Unit,
    onCriar: (Boolean) -> Unit,
    onConectar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editando by rememberSaveable { mutableIntStateOf(0) }
    var novoGrupo by rememberSaveable { mutableStateOf(false) }
    var cancelando by rememberSaveable { mutableStateOf(false) }

    val paraCriar = itensParaCriar(plano, analises)
    val falhas = plano.itens.filter { it.estado == EstadoItem.FALHOU }
    val existente = grupoPorNome(grupos, plano.grupoNome)
    val mudaCor = existente != null && plano.cor != null && plano.cor != existente.cor
    val criados = plano.itens.count { it.estado == EstadoItem.CRIADO }

    fun mudarItem(n: Int, mudanca: (ItemLote) -> ItemLote) {
        onMudar(plano.copy(itens = plano.itens.map { if (it.n == n) mudanca(it) else it }))
    }

    Cartao(modifier) {
        Column(Modifier.padding(Espaco.md), verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (plano.concluido) plural(criados, "evento criado", "eventos criados") else plural(plano.itens.size, "compromisso"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (!criando) {
                    IconButton(onClick = { if (plano.concluido) onMudar(null) else cancelando = true }) {
                        Icon(Icones.Fechar, contentDescription = if (plano.concluido) "Fechar" else "Cancelar a lista")
                    }
                }
            }

            plano.itens.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinhaItem(
                    item = item,
                    plano = plano,
                    analise = analises[item.n],
                    ativo = !criando,
                    onEditar = { editando = item.n },
                    onExcluir = {
                        val resto = plano.itens.filter { it.n != item.n }
                        onMudar(if (resto.isEmpty()) null else plano.copy(itens = resto))
                    },
                    onCriarMesmoAssim = { mudarItem(item.n) { it.copy(permitirRepetido = true) } }
                )
            }

            if (!plano.concluido && !criando) {
                Rotulo("Grupo")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
                    verticalArrangement = Arrangement.spacedBy(Espaco.xs)
                ) {
                    FilterChip(
                        selected = plano.grupoDecidido && !plano.temGrupo,
                        onClick = { onMudar(semGrupo(plano)) },
                        label = { Text("Sem grupo") }
                    )
                    grupos.forEach { g ->
                        FilterChip(
                            selected = plano.temGrupo && existente?.id == g.id,
                            onClick = { onMudar(escolherGrupo(plano, g.nome, grupos).copy(corDecidida = true)) },
                            label = { Text(g.nome) },
                            leadingIcon = { PontoCor(g.cor) }
                        )
                    }
                    // Grupo novo escolhido, ou a sugestão da IA, que ainda não existe
                    val nomeNovo = plano.grupoNome.takeIf { plano.temGrupo && existente == null }
                        ?: plano.grupoSugerido.takeIf { it.isNotBlank() && grupoPorNome(grupos, it) == null }
                    if (nomeNovo != null) {
                        FilterChip(
                            selected = plano.temGrupo && existente == null,
                            onClick = { onMudar(escolherGrupo(plano, nomeNovo, grupos).copy(cor = plano.cor ?: corSugerida(grupos))) },
                            label = { Text(nomeNovo) }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { novoGrupo = true },
                        label = { Text("Novo grupo") },
                        leadingIcon = { Icon(Icones.Adicionar, contentDescription = null, Modifier.size(18.dp)) }
                    )
                }

                if (plano.temGrupo) {
                    Rotulo("Cor")
                    PaletaCores(plano.cor) { onMudar(plano.copy(cor = it, corDecidida = true)) }
                    if (mudaCor && existente != null) {
                        Aviso("Os eventos que já estão em ${existente.nome} também vão mudar de cor.")
                    }
                }

                Rotulo("Avisos")
                SeletorAvisos(plano.avisos) { onMudar(plano.copy(avisos = it)) }

                Rotulo("Duração")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
                    verticalArrangement = Arrangement.spacedBy(Espaco.xs)
                ) {
                    OPCOES_DURACAO_LOTE.forEach { minutos ->
                        FilterChip(
                            selected = plano.duracaoPadrao == minutos,
                            onClick = { onMudar(plano.copy(duracaoPadrao = minutos)) },
                            label = { Text(rotuloDuracao(minutos)) }
                        )
                    }
                }

                when {
                    !agendaLiberada -> {
                        Aviso("Sem acesso à agenda: não dá para conferir conflitos.")
                        OutlinedButton(onClick = onConectar) { Text("Conectar Google Agenda") }
                    }
                    !agendaGoogle -> Aviso("Nenhuma conta Google neste celular: os eventos vão para a agenda do aparelho, sem cor de grupo.")
                }
            }

            if (criando) {
                Row(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Espaco.md))
                    Text("Criando $criados de ${plano.itens.size}…", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (!plano.concluido) {
                val pronto = planoPronto(plano)
                Button(
                    onClick = { onCriar(false) },
                    enabled = pronto && paraCriar.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(
                        when {
                            falhas.isNotEmpty() -> "Tentar de novo (${paraCriar.size})"
                            paraCriar.isEmpty() -> "Nada novo para criar"
                            else -> "Criar ${plural(paraCriar.size, "evento")}"
                        }
                    )
                }
                if (!pronto) {
                    Text(
                        "Falta: " + listOfNotNull(
                            "grupo".takeIf { !plano.grupoDecidido },
                            "cor".takeIf { plano.temGrupo && (plano.cor == null || !plano.corDecidida) },
                            "avisos".takeIf { plano.avisos == null }
                        ).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (falhas.isNotEmpty()) {
                    TextButton(onClick = { onCriar(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Salvar só no Folgui")
                    }
                }
            }
        }
    }

    plano.itens.firstOrNull { it.n == editando }?.let { item ->
        DialogoItem(
            item = item,
            plano = plano,
            onSalvar = { novo ->
                editando = 0
                mudarItem(item.n) { novo }
            },
            onFechar = { editando = 0 }
        )
    }

    if (novoGrupo) {
        DialogoGrupo(
            inicial = null,
            grupos = grupos,
            nomeSugerido = plano.grupoSugerido.takeIf { grupoPorNome(grupos, it) == null }.orEmpty(),
            onSalvar = { nome, cor ->
                novoGrupo = false
                onMudar(escolherGrupo(plano, nome, grupos).copy(cor = cor, corDecidida = true))
            },
            onFechar = { novoGrupo = false }
        )
    }

    if (cancelando) {
        AlertDialog(
            onDismissRequest = { cancelando = false },
            title = { Text("Cancelar a lista?") },
            text = { if (criados > 0) Text("${plural(criados, "evento")} já criado continua na agenda.") },
            confirmButton = {
                TextButton(onClick = {
                    cancelando = false
                    onMudar(null)
                }) { Text("Cancelar a lista") }
            },
            dismissButton = { TextButton(onClick = { cancelando = false }) { Text("Voltar") } }
        )
    }
}

@Composable
private fun Aviso(texto: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(corAmbarFundo(), MaterialTheme.shapes.small)
            .padding(Espaco.sm),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icones.Aviso, contentDescription = null, tint = corAmbar(), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Espaco.sm))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = corAmbarConteudo())
    }
}

/** Avisos com vários ligados ao mesmo tempo; "Sem aviso" limpa a lista. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeletorAvisos(avisos: List<Int>?, onMudar: (List<Int>) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
        verticalArrangement = Arrangement.spacedBy(Espaco.xs)
    ) {
        OPCOES_LEMBRETE.forEach { opcao ->
            val semAviso = opcao.minutos < 0
            val marcada = if (semAviso) avisos?.isEmpty() == true else avisos.orEmpty().contains(opcao.minutos)
            FilterChip(
                selected = marcada,
                onClick = {
                    onMudar(
                        when {
                            semAviso -> emptyList()
                            marcada -> avisos.orEmpty() - opcao.minutos
                            else -> normalizarAvisos(avisos.orEmpty() + opcao.minutos)
                        }
                    )
                },
                label = { Text(if (semAviso) "Sem aviso" else opcao.rotulo) }
            )
        }
    }
}

@Composable
private fun LinhaItem(
    item: ItemLote,
    plano: PlanoLote,
    analise: AnaliseItem?,
    ativo: Boolean,
    onEditar: () -> Unit,
    onExcluir: () -> Unit,
    onCriarMesmoAssim: () -> Unit
) {
    val repetido = analise?.repetido == true && !item.permitirRepetido
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeloData(item.dia)
            Spacer(Modifier.width(Espaco.sm))
            Column(Modifier.weight(1f)) {
                Text("${item.n}. ${item.titulo}", style = MaterialTheme.typography.titleSmall)
                Text(
                    listOf(
                        item.horario,
                        rotuloDuracao(duracaoDoItem(item, plano)),
                        item.local,
                        item.avisos?.let(::rotuloAvisos).orEmpty()
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                item.estado == EstadoItem.CRIADO -> Box(Modifier.size(Espaco.alvoToque), contentAlignment = Alignment.Center) {
                    Icon(Icones.Confirmar, contentDescription = "Criado", tint = corIndicador(StatusDia.FOLGA))
                }
                ativo -> {
                    IconButton(onClick = onEditar) { Icon(Icones.Editar, contentDescription = "Editar ${item.titulo}") }
                    IconButton(onClick = onExcluir) { Icon(Icones.Excluir, contentDescription = "Tirar ${item.titulo} da lista") }
                }
            }
        }
        if (item.estado == EstadoItem.FALHOU) {
            Text(
                "Falhou: ${item.erro}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (repetido) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Já está na agenda, fica de fora",
                    style = MaterialTheme.typography.bodySmall,
                    color = corAmbarConteudo(),
                    modifier = Modifier.weight(1f)
                )
                if (ativo) TextButton(onClick = onCriarMesmoAssim) { Text("Criar mesmo assim") }
            }
        }
        if (item.estado != EstadoItem.CRIADO) analise?.conflitos.orEmpty().forEach { Aviso(it) }
    }
}

/** Correção de um compromisso da lista, sem mexer nos outros. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoItem(item: ItemLote, plano: PlanoLote, onSalvar: (ItemLote) -> Unit, onFechar: () -> Unit) {
    val context = LocalContext.current
    var titulo by rememberSaveable { mutableStateOf(item.titulo) }
    var local by rememberSaveable { mutableStateOf(item.local) }
    var dia by rememberSaveable { mutableIntStateOf(item.dia) }
    var horario by rememberSaveable { mutableStateOf(item.horario) }
    var duracao by rememberSaveable { mutableIntStateOf(item.duracaoMin ?: 0) }
    var proprios by rememberSaveable { mutableStateOf(item.avisos != null) }
    var avisos by rememberSaveable { mutableStateOf(avisosDoItem(item, plano).joinToString(",")) }
    val listaAvisos = avisos.split(",").mapNotNull { it.toIntOrNull() }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Editar compromisso") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Espaco.sm)
            ) {
                CampoTexto(
                    valor = titulo,
                    onValorChange = { titulo = it.take(TAMANHO_TITULO) },
                    rotulo = "Nome",
                    erro = "Dê um nome".takeIf { titulo.isBlank() },
                    modifier = Modifier.fillMaxWidth()
                )
                CampoTexto(
                    valor = local,
                    onValorChange = { local = it.take(TAMANHO_TITULO) },
                    rotulo = "Local (opcional)",
                    icone = Icones.Local,
                    acaoTeclado = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth()
                )
                CampoSelecao(
                    valor = diaResumido(dia),
                    rotulo = "Dia",
                    icone = Icones.Calendario,
                    onClick = { abrirSeletorData(context, dia) { dia = it } },
                    modifier = Modifier.fillMaxWidth()
                )
                CampoSelecao(
                    valor = horario,
                    rotulo = "Hora",
                    icone = Icones.Relogio,
                    onClick = { abrirSeletorHorario(context, horario) { horario = it } },
                    modifier = Modifier.fillMaxWidth()
                )
                Rotulo("Duração")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
                    verticalArrangement = Arrangement.spacedBy(Espaco.xs)
                ) {
                    FilterChip(
                        selected = duracao == 0,
                        onClick = { duracao = 0 },
                        label = { Text("Padrão (${rotuloDuracao(plano.duracaoPadrao)})") }
                    )
                    OPCOES_DURACAO_LOTE.forEach { minutos ->
                        FilterChip(
                            selected = duracao == minutos,
                            onClick = { duracao = minutos },
                            label = { Text(rotuloDuracao(minutos)) }
                        )
                    }
                }
                Rotulo("Avisos")
                FilterChip(
                    selected = !proprios,
                    onClick = { proprios = !proprios },
                    label = { Text("Igual aos outros") }
                )
                if (proprios) {
                    SeletorAvisos(listaAvisos) { avisos = it.joinToString(",") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = titulo.isNotBlank(),
                onClick = {
                    onSalvar(
                        item.copy(
                            titulo = titulo.trim(),
                            local = local.trim(),
                            dia = dia,
                            horario = horario,
                            duracaoMin = duracao.takeIf { it > 0 },
                            avisos = listaAvisos.takeIf { proprios },
                            // Mudou o que identifica o compromisso: a conferência começa de novo
                            permitirRepetido = item.permitirRepetido && titulo.trim() == item.titulo && dia == item.dia && horario == item.horario
                        )
                    )
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )
}
