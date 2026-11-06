package com.example.folgui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow

// ============================================================================
// Telas dos amigos conectados: código, convites e a escala de quem usa o app
// ============================================================================

fun compartilharCodigo(context: Context, codigo: String) {
    val envio = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, mensagemCodigo(codigo))
    context.startActivity(Intent.createChooser(envio, "Enviar meu código"))
}

/** Topo da aba Pessoas: conectar com quem também tem o app. */
@Composable
fun CartaoAmigos(
    temConta: Boolean,
    codigo: String?,
    falhou: Boolean,
    onEntrar: () -> Unit,
    onCompartilhar: (String) -> Unit,
    onAdicionar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Cartao(modifier.fillMaxWidth(), cor = MaterialTheme.colorScheme.secondaryContainer, elevado = false) {
        Column(Modifier.padding(Espaco.lg), verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
            Text("Amigos com Folgui", style = MaterialTheme.typography.titleMedium)
            when {
                !temConta -> {
                    Text("Entre na sua conta para ver a escala dos amigos sem digitar nada.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onEntrar, modifier = Modifier.fillMaxWidth()) { Text("Entrar com Google") }
                }
                codigo == null -> Text(
                    if (falhou) "Não consegui carregar seu código. Veja a internet." else "Preparando seu código…",
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> {
                    Text("Seu código", style = MaterialTheme.typography.labelMedium)
                    Text(codigoParaMostrar(codigo), style = MaterialTheme.typography.headlineSmall.numerico)
                    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                        Button(onClick = { onCompartilhar(codigo) }, modifier = Modifier.weight(1f)) { Text("Enviar") }
                        FilledTonalButton(onClick = onAdicionar, modifier = Modifier.weight(1f)) { Text("Tenho um código") }
                    }
                }
            }
        }
    }
}

@Composable
fun DialogoCodigoAmigo(erro: String?, ocupado: Boolean, onConfirmar: (String) -> Unit, onFechar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    val codigo = limparCodigo(texto)
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Código do amigo") },
        text = {
            CampoTexto(
                valor = texto,
                onValorChange = { texto = it.take(40) },
                rotulo = "Código",
                placeholder = "ABCD-2345",
                erro = erro,
                capitalizacao = KeyboardCapitalization.Characters,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(codigo) }, enabled = codigoValido(codigo) && !ocupado) {
                Text(if (ocupado) "Conectando…" else "Conectar")
            }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )
}

/** Convite recebido, na tela inicial. Só vira evento se a pessoa aceitar. */
@Composable
fun CartaoConvite(convite: Convite, onAceitar: () -> Unit, onRecusar: () -> Unit, modifier: Modifier = Modifier) {
    Cartao(modifier.fillMaxWidth(), cor = MaterialTheme.colorScheme.secondaryContainer, elevado = false) {
        Column(Modifier.padding(Espaco.md), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            Text(
                "${convite.deNome} te convidou",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(convite.titulo, style = MaterialTheme.typography.titleMedium)
            Text("${diaResumido(convite.dia)}, ${convite.horario}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                Button(onClick = onAceitar, modifier = Modifier.weight(1f)) { Text("Aceitar") }
                OutlinedButton(onClick = onRecusar, modifier = Modifier.weight(1f)) { Text("Recusar") }
            }
        }
    }
}

/** Amigo conectado: a escala vem da conta dele, então aqui ela só aparece. */
@Composable
fun TelaAmigo(amigo: Pessoa, onVoltar: () -> Unit, onDesfazer: () -> Unit) {
    var confirmar by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { BarraSuperior(amigo.nome, onVoltar = onVoltar) }) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .padding(Espaco.lg),
            verticalArrangement = Arrangement.spacedBy(Espaco.lg)
        ) {
            Cartao(Modifier.fillMaxWidth()) { LinhaPessoa(pessoa = amigo, dia = hoje()) }
            Text(
                "Esta escala se atualiza sozinha quando ${amigo.nome} muda algo no Folgui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth()) { Text("Desconectar") }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Desconectar de ${amigo.nome}?") },
            text = { Text("Vocês deixam de ver a escala um do outro.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    onDesfazer()
                }) { Text("Desconectar") }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}
