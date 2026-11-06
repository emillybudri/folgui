package com.example.folgui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ============================================================================
// Atalhos da tela inicial: mudam conforme a semana da pessoa
// ============================================================================

enum class TipoAtalho(@DrawableRes val imagem: Int) {
    ENCONTRO(R.drawable.ilustracao_encontro),
    CHAMAR_AMIGO(R.drawable.ilustracao_encontro),
    PLANEJAR_FOLGA(R.drawable.ilustracao_calendario),
    VER_FOLGA(R.drawable.ilustracao_calendario),
    AMANHA(R.drawable.ilustracao_turnos),
    FERIADO(R.drawable.ilustracao_pronto),
    QUANDO_DA(R.drawable.ilustracao_encontro),
    PERGUNTAR_IA(R.drawable.ilustracao_assistente)
}

data class Atalho(
    val tipo: TipoAtalho,
    val titulo: String,
    val texto: String,
    val dia: Int? = null,
    val encontro: Encontro? = null
)

private const val MAX_ATALHOS = 4

/** Até quantos dias antes um feriado vira atalho. */
private const val DIAS_ATALHO_FERIADO = 30

/** Escolhe, em ordem de utilidade, o que faz sentido agora. */
fun atalhosDoInicio(
    voce: Pessoa,
    pessoas: List<Pessoa>,
    eventos: List<Evento>,
    feriados: Map<Int, String>,
    encontro: Encontro?,
    diaHoje: Int = hoje()
): List<Atalho> = buildList {
    when {
        pessoas.isEmpty() -> add(Atalho(TipoAtalho.CHAMAR_AMIGO, "Chame um amigo", "Vejam as folgas juntos"))
        encontro != null -> add(
            Atalho(
                TipoAtalho.ENCONTRO,
                "Todos livres",
                "${diaResumido(encontro.dia)}, ${horarioDeMinutos(encontro.janela.inicio)}",
                encontro = encontro
            )
        )
    }

    voce.proximoDia(diaHoje + 1, livre = true)?.let { folga ->
        val dia = diaResumido(folga)
        if (eventos.none { it.dia == folga }) {
            add(Atalho(TipoAtalho.PLANEJAR_FOLGA, "Planeje sua folga", "$dia está livre", dia = folga))
        } else {
            add(Atalho(TipoAtalho.VER_FOLGA, "Sua próxima folga", "$dia, já tem evento", dia = folga))
        }
    }

    voce.turnoNoDia(diaHoje + 1)?.let { turno ->
        // Um sono salvo que não combina com o trabalho não vira conselho
        val turnos = voce.todosTurnos
        val acordar = (voce.sono?.takeIf { avisoSono(it, turnos) == null } ?: sonoSugerido(turnos)).fim
        add(
            Atalho(
                TipoAtalho.AMANHA,
                "Amanhã às ${horaCurta(turno.inicio)}",
                "Acorde às ${horaCurta(acordar)}",
                dia = diaHoje + 1
            )
        )
    }

    feriados.keys.sorted().firstOrNull { it in diaHoje + 1..diaHoje + DIAS_ATALHO_FERIADO }?.let { dia ->
        add(Atalho(TipoAtalho.FERIADO, feriados.getValue(dia), rotuloRelativo(dia), dia = dia))
    }

    if (pessoas.isNotEmpty()) add(Atalho(TipoAtalho.QUANDO_DA, "Quando dá?", "Compare as folgas"))
    add(Atalho(TipoAtalho.PERGUNTAR_IA, "Pergunte à IA", "Marque falando"))
}.take(MAX_ATALHOS)

/** Cartões ilustrados, dois por linha. */
@Composable
fun GradeAtalhos(atalhos: List<Atalho>, onAbrir: (Atalho) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espaco.md)) {
        atalhos.chunked(2).forEach { linha ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Espaco.md)
            ) {
                linha.forEach { atalho -> CartaoAtalho(atalho, { onAbrir(atalho) }, Modifier.weight(1f)) }
                if (linha.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CartaoAtalho(atalho: Atalho, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Cartao(modifier.fillMaxHeight(), onClick = onClick) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Espaco.md),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Image(
                painter = painterResource(atalho.tipo.imagem),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            )
            Spacer(Modifier.height(Espaco.xs))
            Text(
                atalho.titulo,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                atalho.texto,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
