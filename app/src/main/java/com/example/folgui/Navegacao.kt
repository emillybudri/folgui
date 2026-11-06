package com.example.folgui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

// ============================================================================
// Navegação entre telas com NavController e NavHost
// ============================================================================
object Rotas {
    const val BOAS_VINDAS = "boas_vindas"
    const val CONFIGURAR = "configurar"
    const val APRESENTACAO = "apresentacao"
    const val INICIO = "inicio"
    const val CALENDARIO = "calendario"
    const val PESSOAS = "pessoas"
    const val EVENTOS = "eventos"
    const val PERFIL = "perfil"
    const val PESSOA = "pessoa/{id}"
    const val DIA = "dia/{dia}"
    const val FORM_EVENTO = "evento/{id}?dia={dia}&hora={hora}&quem={quem}"
    const val EVENTO = "detalhe_evento/{id}"
    const val QUANDO_DA = "quando_da"
    const val ASSISTENTE = "assistente?audio={audio}"

    fun pessoa(id: Int) = "pessoa/$id"
    fun dia(dia: Int) = "dia/$dia"
    fun novoEvento(dia: Int? = null, hora: Int = -1, quem: Set<Int>? = null) =
        "evento/0?dia=${dia ?: -1}&hora=$hora&quem=${participantesParaTexto(quem)}"
    fun editarEvento(id: Int) = "evento/$id"
    fun evento(id: Int) = "detalhe_evento/$id"
    fun assistente(audio: Boolean = false) = "assistente?audio=$audio"
}

private data class Aba(val rota: String, val rotulo: String, val icone: ImageVector)

private val ABAS = listOf(
    Aba(Rotas.INICIO, "Hoje", Icones.Inicio),
    Aba(Rotas.CALENDARIO, "Agenda", Icones.Calendario),
    Aba(Rotas.EVENTOS, "Eventos", Icones.Evento),
    Aba(Rotas.PESSOAS, "Pessoas", Icones.Pessoas)
)

/** Abas com o botão de criar no centro: a ação mais comum fica sempre à mão. */
@Composable
fun BarraAbas(rotaAtual: String?, onIr: (String) -> Unit, onCriar: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = Espaco.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ABAS.take(2).forEach { ItemAba(it, rotaAtual == it.rota) { onIr(it.rota) } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FilledIconButton(onClick = onCriar, modifier = Modifier.size(52.dp)) {
                    Icon(Icones.Adicionar, contentDescription = "Novo evento")
                }
            }
            ABAS.drop(2).forEach { ItemAba(it, rotaAtual == it.rota) { onIr(it.rota) } }
        }
    }
}

@Composable
private fun RowScope.ItemAba(aba: Aba, selecionada: Boolean, onClick: () -> Unit) {
    val cor = if (selecionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(aba.icone, contentDescription = null, tint = cor)
        Text(
            aba.rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = cor,
            fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium
        )
    }
}

fun NavHostController.irParaAba(rota: String) {
    navigate(rota) {
        popUpTo(Rotas.INICIO) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** As abas só aparecem nas telas principais. */
fun rotaTemAbas(rota: String?): Boolean = ABAS.any { it.rota == rota }
