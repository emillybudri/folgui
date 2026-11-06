package com.example.folgui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val SUGESTOES_IA = listOf(
    "Próxima folga?",
    "Marcar compromisso",
    "Remarcar evento",
    "Tirei férias"
)

private val ALTURA_BARRA = 52.dp

private fun tempoAudio(segundos: Int) = "${segundos / 60}:${"%02d".format(segundos % 60)}"

// ============================================================================
// BARRA DA IA: fixa no topo das abas, com a aparência de uma barra de pesquisa
// ============================================================================
@Composable
fun BarraAssistente(nome: String, onAbrir: () -> Unit, onAudio: () -> Unit, onPerfil: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Espaco.lg, vertical = Espaco.sm),
            contentAlignment = Alignment.Center
        ) {
            Row(Modifier.limitarLargura(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(ALTURA_BARRA)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(role = Role.Button, onClickLabel = "Abrir assistente", onClick = onAbrir)
                        .padding(start = Espaco.lg, end = Espaco.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icones.Brilho, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Espaco.md))
                    Text(
                        "Pergunte à IA",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onAudio) {
                        Icon(
                            Icones.Microfone,
                            contentDescription = "Falar com a IA",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(Espaco.sm))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Abrir meu perfil", onClick = onPerfil)
                ) {
                    Avatar(
                        nome,
                        tamanho = 44.dp,
                        cores = MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ============================================================================
// ASSISTENTE: a barra aberta, com a conversa logo abaixo
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaAssistente(
    todas: List<Pessoa>,
    eventos: List<Evento>,
    mensagens: List<MensagemIA>,
    pensando: Boolean,
    iniciarGravando: Boolean,
    snackbar: SnackbarHostState,
    onVoltar: () -> Unit,
    onEnviar: (String) -> Unit,
    onEnviarAudio: (File, Int) -> Unit,
    onAplicar: (Int) -> Unit,
    onDescartar: (Int) -> Unit,
    onLimpar: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gravador = remember { GravadorAudio(context) }
    val foco = remember { FocusRequester() }
    val lista = rememberLazyListState()

    var texto by rememberSaveable { mutableStateOf("") }
    var corrigindo by rememberSaveable { mutableStateOf(false) }
    var gravando by remember { mutableStateOf(false) }
    var segundos by remember { mutableIntStateOf(0) }

    fun avisar(mensagem: String) {
        scope.launch { snackbar.showSnackbar(mensagem) }
    }

    fun comecarGravacao() {
        if (gravador.iniciar()) {
            segundos = 0
            gravando = true
        } else {
            avisar("Microfone indisponível")
        }
    }

    val pedirMicrofone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) comecarGravacao() else avisar("Sem acesso ao microfone")
    }

    fun tocarMicrofone() {
        val permitido = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (permitido) comecarGravacao() else pedirMicrofone.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun terminarGravacao() {
        gravando = false
        val arquivo = gravador.parar()
        if (arquivo == null) {
            avisar("Áudio muito curto")
        } else {
            corrigindo = false
            onEnviarAudio(arquivo, maxOf(segundos, 1))
        }
    }

    fun enviar(pedido: String) {
        if (pedido.isBlank() || pensando) return
        onEnviar(pedido.trim())
        texto = ""
        corrigindo = false
    }

    // Aberta pelo microfone da barra, já começa ouvindo; pelo campo, já abre o teclado
    LaunchedEffect(Unit) {
        if (iniciarGravando) tocarMicrofone() else if (mensagens.isEmpty()) foco.requestFocus()
    }
    LaunchedEffect(gravando) {
        while (gravando) {
            delay(1000)
            segundos++
            if (segundos >= MAX_SEGUNDOS_AUDIO) terminarGravacao()
        }
    }
    DisposableEffect(Unit) { onDispose { gravador.cancelar() } }

    LaunchedEffect(mensagens.size, pensando) {
        val total = mensagens.size + if (pensando) 1 else 0
        if (total > 0) lista.animateScrollToItem(total)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = Espaco.xs, end = Espaco.lg, top = Espaco.sm, bottom = Espaco.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onVoltar) { Icon(Icones.Voltar, contentDescription = "Voltar") }
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = ALTURA_BARRA)
                        .clip(RoundedCornerShape(ALTURA_BARRA / 2))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(start = Espaco.lg, end = Espaco.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (gravando) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                        Spacer(Modifier.width(Espaco.md))
                        Text(
                            "Ouvindo… ${tempoAudio(segundos)}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            gravando = false
                            gravador.cancelar()
                        }) { Icon(Icones.Fechar, contentDescription = "Cancelar áudio") }
                        FilledIconButton(onClick = ::terminarGravacao) {
                            Icon(Icones.Enviar, contentDescription = "Enviar áudio")
                        }
                    } else {
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(vertical = Espaco.sm)
                        ) {
                            if (texto.isEmpty()) {
                                Text(
                                    if (corrigindo) "O que corrijo?" else "Pergunte à IA",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = texto,
                                onValueChange = { texto = it.take(400) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(onSend = { enviar(texto) }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(foco)
                            )
                        }
                        if (texto.isBlank()) {
                            IconButton(onClick = ::tocarMicrofone, enabled = !pensando) {
                                Icon(Icones.Microfone, contentDescription = "Enviar áudio")
                            }
                        } else {
                            FilledIconButton(onClick = { enviar(texto) }, enabled = !pensando) {
                                Icon(Icones.Enviar, contentDescription = "Enviar")
                            }
                        }
                    }
                }
                if (mensagens.isNotEmpty() && !pensando && !gravando) {
                    IconButton(onClick = onLimpar) {
                        Icon(Icones.Adicionar, contentDescription = "Nova conversa")
                    }
                }
            }

            LazyColumn(
                state = lista,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Espaco.lg, vertical = Espaco.md),
                verticalArrangement = Arrangement.spacedBy(Espaco.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item(key = "boas_vindas") {
                    if (mensagens.isEmpty()) {
                        Column(
                            Modifier.limitarLargura(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Espaco.sm)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ilustracao_assistente),
                                contentDescription = null,
                                modifier = Modifier
                                    .height(120.dp)
                                    .aspectRatio(1.5f)
                            )
                            Text("Como posso ajudar?", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Escreva ou fale",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(Espaco.sm))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Espaco.sm, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(Espaco.xs)
                            ) {
                                SUGESTOES_IA.forEach { s ->
                                    SuggestionChip(onClick = { enviar(s) }, label = { Text(s) })
                                }
                            }
                        }
                    }
                }

                itemsIndexed(mensagens) { i, m ->
                    Box(Modifier.limitarLargura()) {
                        if (m.deUsuario) {
                            BalaoUsuario(m, Modifier.align(Alignment.CenterEnd))
                        } else {
                            BalaoIA(
                                mensagem = m,
                                todas = todas,
                                eventos = eventos,
                                onAplicar = { onAplicar(i) },
                                onNaoEIsso = {
                                    onDescartar(i)
                                    corrigindo = true
                                    foco.requestFocus()
                                }
                            )
                        }
                    }
                }

                if (pensando) {
                    item(key = "pensando") {
                        Row(
                            Modifier.limitarLargura(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(Espaco.md))
                            Text(
                                "Conferindo…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalaoUsuario(mensagem: MensagemIA, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(start = 48.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
            .padding(horizontal = Espaco.lg, vertical = Espaco.md)
    ) {
        mensagem.audioSegundos?.let { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icones.Microfone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(Espaco.xs))
                Text(
                    "Áudio de ${tempoAudio(s)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Text(mensagem.texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Resposta da IA: o que ela entendeu, as mudanças, os avisos do app e a confirmação. */
@Composable
private fun BalaoIA(
    mensagem: MensagemIA,
    todas: List<Pessoa>,
    eventos: List<Evento>,
    onAplicar: () -> Unit,
    onNaoEIsso: () -> Unit
) {
    Row(Modifier.padding(end = Espaco.xl), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icones.Brilho,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(Espaco.sm))
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                .padding(Espaco.md),
            verticalArrangement = Arrangement.spacedBy(Espaco.sm)
        ) {
            Text(mensagem.texto, style = MaterialTheme.typography.bodyLarge)
            if (mensagem.acoes.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
                        .padding(Espaco.md),
                    verticalArrangement = Arrangement.spacedBy(Espaco.sm)
                ) {
                    if (mensagem.entendi.isNotBlank()) {
                        Text(
                            "Entendi:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(mensagem.entendi, style = MaterialTheme.typography.titleSmall)
                    }
                    mensagem.acoes.forEachIndexed { i, a ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                when (a.tipo) {
                                    TipoAcaoIA.FERIAS, TipoAcaoIA.REMOVER_FERIAS -> Icones.Sol
                                    TipoAcaoIA.FOLGA, TipoAcaoIA.AUSENCIA -> Icones.DiaLivre
                                    TipoAcaoIA.REMOVER_MUDANCA -> Icones.Sincronizar
                                    TipoAcaoIA.SONO -> Icones.Lua
                                    TipoAcaoIA.CRIAR_EVENTO, TipoAcaoIA.EDITAR_EVENTO -> Icones.Evento
                                    TipoAcaoIA.EXCLUIR_EVENTO -> Icones.Excluir
                                    else -> if (a.turno?.noturno == true) Icones.Lua else Icones.Trabalho
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(Espaco.sm))
                            Text(
                                mensagem.descricoes.getOrNull(i) ?: descreverAcao(a, todas, eventos),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            mensagem.avisos.forEach { aviso ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(corAmbarFundo(), MaterialTheme.shapes.small)
                        .padding(Espaco.sm),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icones.Aviso, contentDescription = null, tint = corAmbar(), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Espaco.sm))
                    Text(aviso, style = MaterialTheme.typography.bodyMedium, color = corAmbarConteudo())
                }
            }
            if (mensagem.acoes.isNotEmpty()) {
                when (mensagem.situacao) {
                    SituacaoAcoes.PENDENTE -> Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                        Button(onClick = onAplicar) {
                            Icon(Icones.Confirmar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Espaco.sm))
                            Text("É isso")
                        }
                        OutlinedButton(onClick = onNaoEIsso) { Text("Não é isso") }
                    }
                    SituacaoAcoes.APLICADAS -> Text(
                        "✓ Feito",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SituacaoAcoes.DESCARTADAS -> Text(
                        "Sem mudanças",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
