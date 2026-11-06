package com.example.folgui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

// ============================================================================
// Horário de sono: o Folgui não sugere nem marca nada nesse trecho
// ============================================================================

/** Campos de dormir e acordar, com a duração do sono logo abaixo. */
@Composable
fun CamposSono(dormir: Int, acordar: Int, turnos: List<Turno>, onDormir: (Int) -> Unit, onAcordar: (Int) -> Unit) {
    val context = LocalContext.current
    val aviso = avisoSono(Turno(dormir, acordar), turnos)
    Column(verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espaco.md)) {
            CampoSelecao(
                valor = horarioDeMinutos(dormir),
                rotulo = "Dormir",
                icone = Icones.Lua,
                onClick = { abrirSeletorHorario(context, horarioDeMinutos(dormir)) { onDormir(minutosDoHorario(it)) } },
                modifier = Modifier.weight(1f)
            )
            CampoSelecao(
                valor = horarioDeMinutos(acordar),
                rotulo = "Acordar",
                icone = Icones.Sol,
                onClick = { abrirSeletorHorario(context, horarioDeMinutos(acordar)) { onAcordar(minutosDoHorario(it)) } },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "${Turno(dormir, acordar).duracaoTexto} de sono",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (aviso != null) {
            val sugestao = sonoSugerido(turnos)
            Text(aviso, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                onDormir(sugestao.inicio)
                onAcordar(sugestao.fim)
            }) { Text("Usar ${sugestao.resumo}") }
        }
    }
}

@Composable
fun DialogoSono(sono: Turno?, turnos: List<Turno>, onSalvar: (Turno?) -> Unit, onFechar: () -> Unit) {
    val base = sono ?: sonoSugerido(turnos)
    var dormir by rememberSaveable { mutableIntStateOf(base.inicio) }
    var acordar by rememberSaveable { mutableIntStateOf(base.fim) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Sono") },
        text = { CamposSono(dormir, acordar, turnos, onDormir = { dormir = it }, onAcordar = { acordar = it }) },
        confirmButton = { TextButton(onClick = { onSalvar(Turno(dormir, acordar)) }) { Text("Salvar") } },
        dismissButton = {
            if (sono != null) {
                TextButton(onClick = { onSalvar(null) }) { Text("Remover") }
            } else {
                TextButton(onClick = onFechar) { Text("Cancelar") }
            }
        }
    )
}
