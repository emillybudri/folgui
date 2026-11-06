package com.example.folgui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ============================================================================
// Apresentação do app, com frases curtas e uma ideia por página
// ============================================================================

private class PaginaApresentacao(@DrawableRes val imagem: Int, val titulo: String, val texto: String)

private val PAGINAS = listOf(
    PaginaApresentacao(
        R.drawable.ilustracao_pronto,
        "Sua escala já está aqui",
        "Na aba Hoje você vê se trabalha ou folga, sem fazer conta."
    ),
    PaginaApresentacao(
        R.drawable.ilustracao_calendario,
        "Toque em um dia",
        "Na aba Agenda, cada dia mostra sua escala e seus compromissos."
    ),
    PaginaApresentacao(
        R.drawable.ilustracao_encontro,
        "Veja quem está livre",
        "Na aba Pessoas, envie seu código para um amigo. As escalas de vocês ficam juntas."
    ),
    PaginaApresentacao(
        R.drawable.ilustracao_assistente,
        "Peça falando",
        "Toque no microfone e diga o que quer marcar. Nada é salvo sem você confirmar."
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelaApresentacao(modoFacil: Boolean, onModoFacil: (Boolean) -> Unit, onConcluir: () -> Unit) {
    val paginas = rememberPagerState { PAGINAS.size + 1 }
    val scope = rememberCoroutineScope()
    val ultima = paginas.currentPage == PAGINAS.lastIndex + 1

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Espaco.sm),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onConcluir) { Text("Pular") }
        }

        HorizontalPager(state = paginas, modifier = Modifier.weight(1f)) { indice ->
            val pagina = PAGINAS.getOrNull(indice)
            if (pagina != null) {
                PaginaIlustrada(pagina)
            } else {
                PaginaLetras(modoFacil, onModoFacil)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(Espaco.md),
            horizontalArrangement = Arrangement.spacedBy(Espaco.sm, Alignment.CenterHorizontally)
        ) {
            repeat(paginas.pageCount) { i ->
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            if (i == paginas.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            CircleShape
                        )
                )
            }
        }

        Button(
            onClick = {
                if (ultima) onConcluir() else scope.launch { paginas.animateScrollToPage(paginas.currentPage + 1) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Espaco.lg, vertical = Espaco.md)
                .heightIn(min = 60.dp)
        ) {
            Text(if (ultima) "Começar" else "Próximo", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PaginaIlustrada(pagina: PaginaApresentacao) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Espaco.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espaco.lg, Alignment.CenterVertically)
    ) {
        Image(
            painter = painterResource(pagina.imagem),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        )
        Text(pagina.titulo, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(pagina.texto, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/** Última página: a pessoa vê o texto mudar de tamanho na hora. */
@Composable
private fun PaginaLetras(modoFacil: Boolean, onModoFacil: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Espaco.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espaco.lg, Alignment.CenterVertically)
    ) {
        Text("Prefere letras maiores?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "O modo fácil aumenta os textos do app inteiro. Dá para mudar depois em Minha escala.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Espaco.sm))
        LinhaModoFacil(modoFacil, onModoFacil)
    }
}

@Composable
fun LinhaModoFacil(ligado: Boolean, onMudar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Modo fácil", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = ligado, onCheckedChange = onMudar)
    }
}
