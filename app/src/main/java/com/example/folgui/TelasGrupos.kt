package com.example.folgui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================================
// Grupos: faixa na aba Eventos, tela do grupo e os seletores usados nos formulários.
// A cor nunca vem sozinha: o nome do grupo ou da cor sempre aparece junto.
// ============================================================================

val CorGrupo.cor: Color get() = Color(argb)

/** Ícone legível sobre a cor: preto nas claras, branco nas escuras. */
private val CorGrupo.corDoIcone: Color get() = if (cor.luminance() > 0.4f) Color.Black else Color.White

@Composable
fun PontoCor(cor: CorGrupo, tamanho: Dp = 12.dp) {
    Box(
        Modifier
            .size(tamanho)
            .background(cor.cor, CircleShape)
    )
}

/** Ponto e nome do grupo, na linha de um evento. */
@Composable
fun ResumoGrupo(grupo: Grupo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PontoCor(grupo.cor, 8.dp)
        Spacer(Modifier.width(Espaco.xs))
        Text(
            grupo.nome,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** As 11 cores da Google Agenda: é a lista que sincroniza de verdade. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletaCores(selecionada: CorGrupo?, onEscolher: (CorGrupo) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.xs)
        ) {
            CorGrupo.entries.forEach { c ->
                val marcada = c == selecionada
                Box(
                    Modifier
                        .size(Espaco.alvoToque)
                        .selectable(selected = marcada, role = Role.RadioButton, onClick = { onEscolher(c) })
                        .semantics { contentDescription = "${c.nome}, ${c.comum}" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(c.cor, CircleShape)
                            .then(
                                if (marcada) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (marcada) {
                            Icon(Icones.Confirmar, contentDescription = null, tint = c.corDoIcone, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        selecionada?.let {
            Text(it.nome, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Escolha do grupo no formulário do evento. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeletorGrupo(grupos: List<Grupo>, selecionado: Int, onEscolher: (Int) -> Unit, onNovo: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
        verticalArrangement = Arrangement.spacedBy(Espaco.xs)
    ) {
        FilterChip(selected = selecionado == 0, onClick = { onEscolher(0) }, label = { Text("Sem grupo") })
        grupos.forEach { g ->
            FilterChip(
                selected = selecionado == g.id,
                onClick = { onEscolher(g.id) },
                label = { Text(g.nome) },
                leadingIcon = { PontoCor(g.cor) }
            )
        }
        FilterChip(
            selected = false,
            onClick = onNovo,
            label = { Text("Novo grupo") },
            leadingIcon = { Icon(Icones.Adicionar, contentDescription = null, Modifier.size(18.dp)) }
        )
    }
}

@Composable
fun DialogoGrupo(
    inicial: Grupo?,
    grupos: List<Grupo>,
    onSalvar: (String, CorGrupo) -> Unit,
    onFechar: () -> Unit,
    nomeSugerido: String = ""
) {
    var nome by rememberSaveable { mutableStateOf(inicial?.nome ?: nomeSugerido) }
    var corId by rememberSaveable { mutableIntStateOf((inicial?.cor ?: corSugerida(grupos)).id) }
    var tentou by rememberSaveable { mutableStateOf(false) }
    val erro = erroNomeGrupo(nome, grupos, inicial?.id ?: 0)
    val cor = CorGrupo.porId(corId) ?: CorGrupo.PAVAO

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(if (inicial == null) "Novo grupo" else "Editar grupo") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Espaco.md)
            ) {
                CampoTexto(
                    valor = nome,
                    onValorChange = { nome = it.take(40) },
                    rotulo = "Nome",
                    erro = erro.takeIf { tentou },
                    acaoTeclado = androidx.compose.ui.text.input.ImeAction.Done,
                    modifier = Modifier.fillMaxWidth()
                )
                PaletaCores(cor) { corId = it.id }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tentou = true
                if (erro == null) onSalvar(nome.trim(), cor)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )
}

// ----------------------------------------------------------------------------
// Aba Eventos
// ----------------------------------------------------------------------------

/** Cartões dos grupos no topo da aba Eventos. */
@Composable
fun FaixaGrupos(
    grupos: List<Grupo>,
    eventos: List<Evento>,
    onAbrir: (Int) -> Unit,
    onNovo: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Espaco.lg, vertical = Espaco.xs),
        horizontalArrangement = Arrangement.spacedBy(Espaco.sm)
    ) {
        items(grupos, key = { it.id }) { g ->
            CartaoGrupo(g, eventosDoGrupo(eventos, g.id).size) { onAbrir(g.id) }
        }
        item(key = "novo") {
            Cartao(Modifier.height(72.dp), onClick = onNovo) {
                Row(
                    Modifier
                        .fillMaxHeight()
                        .padding(horizontal = Espaco.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icones.Adicionar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Espaco.sm))
                    Text("Novo grupo", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CartaoGrupo(grupo: Grupo, quantidade: Int, onClick: () -> Unit) {
    Cartao(
        Modifier
            .width(168.dp)
            .height(72.dp),
        onClick = onClick
    ) {
        Row {
            Box(
                Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(grupo.cor.cor)
            )
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = Espaco.md),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    grupo.nome,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${plural(quantidade, "evento")} · ${grupo.cor.nome}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Detalhe do evento
// ----------------------------------------------------------------------------

@Composable
fun LinhaGrupoDoEvento(grupo: Grupo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Espaco.alvoToque)
            .clickable(role = Role.Button, onClickLabel = "Abrir grupo ${grupo.nome}", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { PontoCor(grupo.cor, 16.dp) }
        Spacer(Modifier.width(Espaco.lg))
        Text(grupo.nome, style = MaterialTheme.typography.bodyLarge)
    }
}

/** O evento foi apagado direto na Google Agenda: a pessoa decide o que fazer. */
@Composable
fun AvisoForaDaAgenda(onRestaurar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(corAmbarFundo(), MaterialTheme.shapes.medium)
            .padding(start = Espaco.md, end = Espaco.xs, top = Espaco.xs, bottom = Espaco.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icones.Aviso, contentDescription = null, tint = corAmbar(), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Espaco.sm))
        Text(
            "Apagado da Google Agenda",
            style = MaterialTheme.typography.bodyMedium,
            color = corAmbarConteudo(),
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRestaurar) { Text("Restaurar") }
    }
}

// ----------------------------------------------------------------------------
// Tela do grupo
// ----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaGrupo(
    grupo: Grupo?,
    grupos: List<Grupo>,
    eventos: List<Evento>,
    todas: List<Pessoa>,
    compromissos: List<CompromissoAgenda>,
    snackbar: SnackbarHostState,
    onVoltar: () -> Unit,
    onAbrirEvento: (Int) -> Unit,
    onNovoEvento: () -> Unit,
    onEditar: (Grupo, Grupo) -> Unit,
    onExcluir: (Grupo, Boolean) -> Unit,
    onMover: (List<Evento>, Grupo?) -> Unit,
    onImportar: (CompromissoAgenda, Grupo) -> Unit,
    onRestaurar: (Evento) -> Unit,
    onExcluirEvento: (Evento) -> Unit
) {
    // Mantém o último grupo exibido para a tela não piscar enquanto sai após a exclusão
    var exibido by remember { mutableStateOf(grupo) }
    LaunchedEffect(grupo) { if (grupo != null) exibido = grupo }
    val atual = exibido

    var verPassados by rememberSaveable { mutableStateOf(false) }
    var editando by rememberSaveable { mutableStateOf(false) }
    var excluindo by rememberSaveable { mutableStateOf(false) }
    var adicionando by rememberSaveable { mutableStateOf(false) }
    var movendo by rememberSaveable { mutableIntStateOf(0) }
    var novaCor by remember { mutableStateOf<Grupo?>(null) }

    Scaffold(
        topBar = {
            BarraSuperior(titulo = atual?.nome ?: "Grupo", onVoltar = onVoltar) {
                if (atual != null) {
                    IconButton(onClick = { editando = true }) {
                        Icon(Icones.Editar, contentDescription = "Editar grupo")
                    }
                    IconButton(onClick = { excluindo = true }) {
                        Icon(Icones.Excluir, contentDescription = "Excluir grupo")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        if (atual == null) {
            EstadoVazio(
                icone = Icones.Grade,
                titulo = "Grupo não encontrado",
                texto = "Ele pode ter sido excluído.",
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        val doGrupo = eventosDoGrupo(eventos, atual.id)
        val apagados = doGrupo.filter { it.foraDaAgenda }
        val ativos = doGrupo - apagados.toSet()
        val proximos = ativos.filter { !it.jaPassou }
        val passados = ativos.filter { it.jaPassou }.reversed()
        val lista = if (verPassados) passados else proximos

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = Espaco.lg, vertical = Espaco.sm),
            verticalArrangement = Arrangement.spacedBy(Espaco.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "resumo") {
                Column(Modifier.limitarLargura(), verticalArrangement = Arrangement.spacedBy(Espaco.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PontoCor(atual.cor, 16.dp)
                        Spacer(Modifier.width(Espaco.sm))
                        Text(
                            "${atual.cor.nome} · ${plural(doGrupo.size, "evento")}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                        Button(onClick = onNovoEvento, modifier = Modifier.weight(1f)) {
                            Icon(Icones.Adicionar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Espaco.sm))
                            Text("Novo", maxLines = 1)
                        }
                        OutlinedButton(onClick = { adicionando = true }, modifier = Modifier.weight(1f)) {
                            Text("Adicionar", maxLines = 1)
                        }
                    }
                }
            }

            if (apagados.isNotEmpty()) {
                item(key = "apagados") {
                    Text(
                        "Apagados da Google Agenda",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .limitarLargura()
                            .padding(top = Espaco.sm)
                    )
                }
                items(apagados, key = { "a${it.id}" }) { e ->
                    Cartao(Modifier.limitarLargura()) {
                        Column(Modifier.padding(bottom = Espaco.xs)) {
                            LinhaEvento(e, todas, { onAbrirEvento(e.id) })
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Espaco.sm),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onExcluirEvento(e) }) { Text("Excluir") }
                                TextButton(onClick = { onRestaurar(e) }) { Text("Restaurar") }
                            }
                        }
                    }
                }
            }

            item(key = "filtro") {
                Row(
                    Modifier
                        .limitarLargura()
                        .padding(top = Espaco.sm),
                    horizontalArrangement = Arrangement.spacedBy(Espaco.sm)
                ) {
                    FilterChip(
                        selected = !verPassados,
                        onClick = { verPassados = false },
                        label = { Text("Próximos (${proximos.size})") }
                    )
                    FilterChip(
                        selected = verPassados,
                        onClick = { verPassados = true },
                        label = { Text("Anteriores (${passados.size})") }
                    )
                }
            }

            if (lista.isEmpty()) {
                item(key = "vazio") {
                    Text(
                        if (verPassados) "Nada por aqui" else "Sem eventos marcados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .limitarLargura()
                            .padding(vertical = Espaco.md)
                    )
                }
            }
            items(lista, key = { it.id }) { e ->
                Cartao(Modifier.limitarLargura()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinhaEvento(e, todas, { onAbrirEvento(e.id) }, Modifier.weight(1f))
                        IconButton(onClick = { movendo = e.id }) {
                            Icon(Icones.Proximo, contentDescription = "Mover ${e.titulo} de grupo")
                        }
                    }
                }
            }
        }
    }

    if (atual == null) return

    val movido = eventos.firstOrNull { it.id == movendo }
    if (movido != null) {
        ModalBottomSheet(onDismissRequest = { movendo = 0 }) {
            FolhaMover(movido, grupos - atual) { destino ->
                movendo = 0
                onMover(listOf(movido), destino)
            }
        }
    }

    if (adicionando) {
        ModalBottomSheet(onDismissRequest = { adicionando = false }) {
            FolhaAdicionar(
                eventos = eventos.filter { it.grupoId != atual.id && !it.foraDaAgenda },
                compromissos = compromissos
                    .filter { !it.diaInteiro && !it.recorrente && it.editavel }
                    .distinctBy { it.eventoId },
                onEvento = { e ->
                    adicionando = false
                    onMover(listOf(e), atual)
                },
                onCompromisso = { c ->
                    adicionando = false
                    onImportar(c, atual)
                }
            )
        }
    }

    if (editando) {
        DialogoGrupo(
            inicial = atual,
            grupos = grupos,
            onSalvar = { nome, cor ->
                editando = false
                val depois = atual.copy(nome = nome, cor = cor)
                // Mudar a cor repinta a agenda inteira do grupo: isso pede confirmação
                if (cor != atual.cor && eventosDoGrupo(eventos, atual.id).isNotEmpty()) {
                    novaCor = depois
                } else {
                    onEditar(atual, depois)
                }
            },
            onFechar = { editando = false }
        )
    }

    novaCor?.let { depois ->
        val quantos = eventosDoGrupo(eventos, atual.id).size
        AlertDialog(
            onDismissRequest = { novaCor = null },
            title = { Text("Mudar a cor?") },
            text = { Text("${plural(quantos, "evento")} deste grupo vão ficar com a cor ${depois.cor.comum} na Google Agenda.") },
            confirmButton = {
                TextButton(onClick = {
                    novaCor = null
                    onEditar(atual, depois)
                }) { Text("Mudar") }
            },
            dismissButton = { TextButton(onClick = { novaCor = null }) { Text("Cancelar") } }
        )
    }

    if (excluindo) {
        val quantos = eventosDoGrupo(eventos, atual.id).size
        AlertDialog(
            onDismissRequest = { excluindo = false },
            title = { Text("Excluir ${atual.nome}?") },
            text = {
                if (quantos > 0) {
                    Text("${plural(quantos, "evento")} neste grupo. Mantidos, eles voltam para a cor padrão da agenda.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    excluindo = false
                    onExcluir(atual, false)
                }) { Text(if (quantos > 0) "Manter eventos" else "Excluir") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { excluindo = false }) { Text("Cancelar") }
                    if (quantos > 0) {
                        TextButton(
                            onClick = {
                                excluindo = false
                                onExcluir(atual, true)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Excluir tudo") }
                    }
                }
            }
        )
    }
}

@Composable
private fun FolhaMover(evento: Evento, destinos: List<Grupo>, onEscolher: (Grupo?) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Espaco.lg, end = Espaco.lg, bottom = Espaco.xl),
        verticalArrangement = Arrangement.spacedBy(Espaco.sm)
    ) {
        Text("Mover", style = MaterialTheme.typography.titleLarge)
        Text(
            evento.titulo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        destinos.forEach { g ->
            FilledTonalButton(onClick = { onEscolher(g) }, modifier = Modifier.fillMaxWidth()) {
                PontoCor(g.cor)
                Spacer(Modifier.width(Espaco.sm))
                Text(g.nome)
            }
        }
        OutlinedButton(onClick = { onEscolher(null) }, modifier = Modifier.fillMaxWidth()) { Text("Tirar do grupo") }
    }
}

/** Eventos que podem entrar no grupo: os do Folgui e os criados direto na Google Agenda. */
@Composable
private fun FolhaAdicionar(
    eventos: List<Evento>,
    compromissos: List<CompromissoAgenda>,
    onEvento: (Evento) -> Unit,
    onCompromisso: (CompromissoAgenda) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = Espaco.lg, end = Espaco.lg, bottom = Espaco.xl),
        verticalArrangement = Arrangement.spacedBy(Espaco.xs)
    ) {
        item { Text("Adicionar ao grupo", style = MaterialTheme.typography.titleLarge) }
        if (eventos.isEmpty() && compromissos.isEmpty()) {
            item {
                Text(
                    "Nenhum evento disponível",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = Espaco.md)
                )
            }
        }
        items(eventos, key = { "f${it.id}" }) { e ->
            LinhaEscolha(e.titulo, "${diaResumido(e.dia)}, ${e.horario}") { onEvento(e) }
        }
        if (compromissos.isNotEmpty()) {
            item {
                Text(
                    "Google Agenda",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Espaco.md)
                )
            }
        }
        items(compromissos, key = { "g${it.eventoId}" }) { c ->
            LinhaEscolha(c.titulo, "${diaResumido(diaDoMomento(c.inicio))}, ${horarioDoMomento(c.inicio)}") { onCompromisso(c) }
        }
    }
}

@Composable
private fun LinhaEscolha(titulo: String, apoio: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Espaco.alvoToque)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Espaco.sm)
    ) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(apoio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
