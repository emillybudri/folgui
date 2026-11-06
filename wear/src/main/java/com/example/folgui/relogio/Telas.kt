package com.example.folgui.relogio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import kotlinx.coroutines.delay

private val Violeta = Color(0xFF6246EA)
private val Lilas = Color(0xFFBDB0FF)
private val Verde = Color(0xFF7BE0B0)
private val Coral = Color(0xFFFFC9BE)

/** Tempo para o celular responder: a IA tenta outros modelos antes de desistir. */
private const val ESPERA_MAXIMA_MS = 60_000L

/** Tempo que o "marcado" fica na tela antes de o app fechar sozinho. */
private const val TEMPO_MARCADO_MS = 4_000L

@Composable
fun TelasRelogio(
    tela: Tela,
    onFalar: () -> Unit,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    onTempoEsgotado: () -> Unit,
    onFechar: () -> Unit
) {
    MaterialTheme(colors = Colors(primary = Violeta, onPrimary = Color.White)) {
        Scaffold(timeText = { TimeText() }) {
            when (tela) {
                Tela.Inicio -> TelaInicio(onFalar)
                Tela.Pensando -> TelaPensando(onTempoEsgotado)
                is Tela.Confirmar -> TelaConfirmar(tela, onConfirmar, onCancelar)
                is Tela.Pergunta -> TelaPergunta(tela.texto, onFalar, onCancelar)
                is Tela.Marcado -> TelaMarcado(tela.texto, onFechar)
                is Tela.Erro -> TelaErro(tela.texto, onFalar)
            }
        }
    }
}

@Composable
private fun TelaInicio(onFalar: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Button(
            onClick = onFalar,
            modifier = Modifier.size(ButtonDefaults.LargeButtonSize),
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Icon(painterResource(R.drawable.ic_microfone), contentDescription = "Falar")
        }
        Text("Toque e diga:", style = MaterialTheme.typography.caption2, color = Lilas)
        Text("“Marque almoço com a Bia amanhã”", textAlign = TextAlign.Center, style = MaterialTheme.typography.caption1)
    }
}

@Composable
private fun TelaPensando(onTempoEsgotado: () -> Unit) {
    // A tela fica ligada só durante a espera, para a resposta aparecer sem tocar no relógio
    val visao = LocalView.current
    DisposableEffect(Unit) {
        visao.keepScreenOn = true
        onDispose { visao.keepScreenOn = false }
    }
    LaunchedEffect(Unit) {
        delay(ESPERA_MAXIMA_MS)
        onTempoEsgotado()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
        CircularProgressIndicator(indicatorColor = Lilas)
        Text("Só um instante…")
    }
}

@Composable
private fun TelaConfirmar(tela: Tela.Confirmar, onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    Lista {
        item { Text("É isso mesmo?", fontWeight = FontWeight.Bold, color = Lilas) }
        item { Text(tela.resumo, textAlign = TextAlign.Center) }
        tela.avisos.forEach { aviso ->
            item { Text(aviso, textAlign = TextAlign.Center, color = Coral, style = MaterialTheme.typography.caption2) }
        }
        item { Opcao("Sim, marcar", principal = true, onConfirmar) }
        item { Opcao("Não", principal = false, onCancelar) }
    }
}

@Composable
private fun TelaPergunta(texto: String, onFalar: () -> Unit, onCancelar: () -> Unit) {
    Lista {
        item { Text(texto, textAlign = TextAlign.Center) }
        item { Opcao("Responder", principal = true, onFalar) }
        item { Opcao("Deixar para lá", principal = false, onCancelar) }
    }
}

@Composable
private fun TelaMarcado(texto: String, onFechar: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(TEMPO_MARCADO_MS)
        onFechar()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Text("Marcado!", fontWeight = FontWeight.Bold, color = Verde, style = MaterialTheme.typography.title2)
        Text(texto, textAlign = TextAlign.Center, style = MaterialTheme.typography.caption1)
    }
}

@Composable
private fun TelaErro(texto: String, onFalar: () -> Unit) {
    Lista {
        item { Text(texto, textAlign = TextAlign.Center, color = Coral) }
        item { Opcao("Tentar de novo", principal = true, onFalar) }
    }
}

/** Lista que acompanha a curva da tela redonda e rola com o dedo. */
@Composable
private fun Lista(conteudo: ScalingLazyListScope.() -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = conteudo
    )
}

@Composable
private fun Opcao(texto: String, principal: Boolean, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(texto) },
        modifier = Modifier.fillMaxWidth(),
        colors = if (principal) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
    )
}
