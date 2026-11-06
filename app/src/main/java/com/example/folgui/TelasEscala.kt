package com.example.folgui

import android.util.Patterns
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ============================================================================
// BOAS-VINDAS (primeiro acesso)
// ============================================================================
@Composable
fun TelaBoasVindas(
    comConta: Boolean,
    entrando: Boolean,
    erro: String?,
    aviso: String?,
    onEntrar: () -> Unit,
    onEntrarEmail: (String, String, Boolean) -> Unit,
    onEsqueciSenha: (String) -> Unit,
    onComecar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espaco.xl, vertical = Espaco.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Espaco.lg))
        Image(
            painter = painterResource(R.drawable.ilustracao_encontro),
            contentDescription = "Duas pessoas e um calendário com o dia livre em comum marcado",
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1.5f)
        )
        Spacer(Modifier.height(Espaco.lg))
        Text("Folgui", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Espaco.sm))
        Text(
            text = "Suas folgas, sem complicação",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.limitarLargura()
        )

        Spacer(Modifier.height(Espaco.xl))

        Row(
            modifier = Modifier.limitarLargura(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Recurso(Icones.Calendario, "Escala")
            Recurso(Icones.Pessoas, "Juntos")
            Recurso(Icones.Brilho, "IA")
        }

        Spacer(Modifier.height(Espaco.xxl))

        if (comConta) {
            BotaoGoogle(
                entrando = entrando,
                onClick = onEntrar,
                modifier = Modifier
                    .limitarLargura()
                    .heightIn(min = 52.dp)
            )
            Spacer(Modifier.height(Espaco.md))
            EntrarComEmail(entrando, onEntrarEmail, onEsqueciSenha)
            listOfNotNull(
                erro?.let { it to MaterialTheme.colorScheme.error },
                aviso?.let { it to MaterialTheme.colorScheme.primary }
            ).forEach { (texto, cor) ->
                Text(
                    texto,
                    style = MaterialTheme.typography.bodySmall,
                    color = cor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Espaco.sm)
                )
            }
        } else {
            Button(
                onClick = onComecar,
                modifier = Modifier
                    .limitarLargura()
                    .heightIn(min = 52.dp)
            ) {
                Text("Começar")
            }
        }
    }
}

@Composable
private fun BotaoGoogle(entrando: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, enabled = !entrando, modifier = modifier) {
        Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Espaco.sm))
        Text(if (entrando) "Entrando" else "Entrar com Google", maxLines = 1)
    }
}

/** Login por e-mail: fechado por padrão, para o Google continuar sendo o caminho mais curto. */
@Composable
private fun EntrarComEmail(
    entrando: Boolean,
    onEntrar: (String, String, Boolean) -> Unit,
    onEsqueciSenha: (String) -> Unit
) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    var criando by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var tentou by remember { mutableStateOf(false) }

    if (!aberto) {
        OutlinedButton(
            onClick = { aberto = true },
            modifier = Modifier
                .limitarLargura()
                .heightIn(min = 52.dp)
        ) {
            Icon(Icones.Email, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Espaco.sm))
            Text("Entrar com e-mail", maxLines = 1)
        }
        return
    }

    val limpo = email.trim()
    val erroEmail = "E-mail inválido".takeIf { !Patterns.EMAIL_ADDRESS.matcher(limpo).matches() }
    val erroSenha = "Use 6 caracteres ou mais".takeIf { senha.length < 6 }

    fun enviar() {
        tentou = true
        if (erroEmail == null && erroSenha == null) onEntrar(limpo, senha, criando)
    }

    Column(Modifier.limitarLargura(), verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
        CampoTexto(
            valor = email,
            onValorChange = { email = it.take(80) },
            rotulo = "E-mail",
            icone = Icones.Email,
            erro = erroEmail.takeIf { tentou },
            capitalizacao = KeyboardCapitalization.None,
            tipoTeclado = KeyboardType.Email,
            modifier = Modifier.fillMaxWidth()
        )
        CampoTexto(
            valor = senha,
            onValorChange = { senha = it.take(64) },
            rotulo = "Senha",
            erro = erroSenha.takeIf { tentou },
            capitalizacao = KeyboardCapitalization.None,
            tipoTeclado = KeyboardType.Password,
            acaoTeclado = ImeAction.Done,
            senha = true,
            onConcluir = ::enviar,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = ::enviar,
            enabled = !entrando,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) {
            Text(if (criando) "Criar conta" else "Entrar")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { criando = !criando }) { Text(if (criando) "Já tenho conta" else "Criar conta") }
            if (!criando) {
                TextButton(
                    onClick = {
                        tentou = true
                        if (erroEmail == null) onEsqueciSenha(limpo)
                    }
                ) { Text("Esqueci a senha") }
            }
        }
    }
}

/** Conta no perfil: com ela, os dados voltam em outro celular. */
@Composable
fun LinhaConta(email: String?, entrando: Boolean, onEntrar: () -> Unit, onSair: () -> Unit) {
    if (email == null) {
        BotaoGoogle(entrando, onEntrar, Modifier.fillMaxWidth())
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icones.Email, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Espaco.sm))
            Text(
                email,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSair) { Text("Sair") }
        }
    }
}

/** A conta e o celular têm dados diferentes: só a pessoa sabe qual vale. */
@Composable
fun DialogoConflitoNuvem(onUsarConta: () -> Unit, onManterCelular: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Qual usar?") },
        text = { Text("Esta conta já tem dados. O outro lado será substituído.") },
        confirmButton = { TextButton(onClick = onUsarConta) { Text("Da conta") } },
        dismissButton = { TextButton(onClick = onManterCelular) { Text("Deste celular") } }
    )
}

@Composable
private fun Recurso(icone: ImageVector, titulo: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(Espaco.xs))
        Text(titulo, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        conteudo()
    }
}

// ============================================================================
// ABERTURA: logo animado enquanto o app carrega (continua a splash do sistema)
// ============================================================================
@Composable
fun TelaAbertura() {
    var visivel by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visivel = true }
    val escala by animateFloatAsState(if (visivel) 1f else 0.6f, tween(600), label = "escala")
    val opacidade by animateFloatAsState(if (visivel) 1f else 0f, tween(500), label = "opacidade")
    val opacidadeTexto by animateFloatAsState(if (visivel) 1f else 0f, tween(700, delayMillis = 250), label = "texto")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF6246EA)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .scale(escala)
                    .alpha(opacidade)
                    .background(Color.White.copy(alpha = 0.16f), MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icones.DiaLivre, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
            }
            Spacer(Modifier.height(Espaco.xl))
            Text(
                "Folgui",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                modifier = Modifier.alpha(opacidadeTexto)
            )
            Spacer(Modifier.height(Espaco.sm))
            Text(
                "Suas folgas, sem complicação",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.alpha(opacidadeTexto)
            )
        }
    }
}
