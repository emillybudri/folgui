package com.example.folgui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry

// ============================================================================
// Transições entre telas: o movimento conta para onde a pessoa foi.
// Ir mais fundo anda para o lado, abrir um formulário sobe, e a troca de aba
// segue a ordem das abas. Tudo curto, para o app nunca parecer lento.
// ============================================================================

enum class TipoTransicao {
    /** Só um fade: login e cadastro, que não têm tela "de onde veio". */
    SUAVE,

    /** Passo curto para o lado, na direção da aba escolhida. */
    ABA,

    /** A nova tela entra pelo lado e a anterior recua um pouco, dando profundidade. */
    PAGINA,

    /** Sobe como uma folha por cima da tela, e desce ao fechar. */
    FOLHA
}

private val ROTAS_ENTRADA = setOf(Rotas.BOAS_VINDAS, Rotas.CONFIGURAR, Rotas.APRESENTACAO)
private val ROTAS_FOLHA = setOf(Rotas.FORM_EVENTO, Rotas.ASSISTENTE)

/** [voltando] é true quando a tela de cima sai da pilha (botão voltar ou salvar). */
fun tipoTransicao(de: String?, para: String?, voltando: Boolean): TipoTransicao = when {
    de in ROTAS_ENTRADA || para in ROTAS_ENTRADA -> TipoTransicao.SUAVE
    rotaTemAbas(de) && rotaTemAbas(para) -> TipoTransicao.ABA
    if (voltando) de in ROTAS_FOLHA else para in ROTAS_FOLHA -> TipoTransicao.FOLHA
    else -> TipoTransicao.PAGINA
}

/** Se o movimento lateral vai para trás: aba anterior, botão voltar ou retorno a uma aba. */
fun andaParaTras(de: String?, para: String?, voltando: Boolean): Boolean =
    if (rotaTemAbas(de) && rotaTemAbas(para)) {
        indiceAba(para) < indiceAba(de)
    } else {
        voltando || rotaTemAbas(para)
    }

private const val DURACAO_ENTRADA = 300
private const val DURACAO_ABA = 240
private const val DURACAO_FECHAR = 200

// A tela antiga some antes de a nova aparecer: com as duas visíveis, os textos se misturam
private const val SOME_RAPIDO = 90
private const val APARECE_DEPOIS = 80

private fun <T> movimento(duracao: Int = DURACAO_ENTRADA): FiniteAnimationSpec<T> =
    tween(duracao, easing = FastOutSlowInEasing)

private fun aparece(duracao: Int = DURACAO_ENTRADA) =
    fadeIn(tween(duracao - APARECE_DEPOIS, delayMillis = APARECE_DEPOIS, easing = LinearOutSlowInEasing))

private fun some() = fadeOut(tween(SOME_RAPIDO, easing = FastOutLinearInEasing))

private typealias EscopoTransicao = AnimatedContentTransitionScope<NavBackStackEntry>

private val EscopoTransicao.origem get() = initialState.destination.route
private val EscopoTransicao.destino get() = targetState.destination.route

/** Start e End, e não esquerda e direita: em idiomas da direita para a esquerda o movimento inverte sozinho. */
private fun EscopoTransicao.sentido(voltando: Boolean) =
    if (andaParaTras(origem, destino, voltando)) SlideDirection.End else SlideDirection.Start

fun EscopoTransicao.entradaDaTela(voltando: Boolean): EnterTransition =
    when (tipoTransicao(origem, destino, voltando)) {
        TipoTransicao.SUAVE -> aparece()
        TipoTransicao.ABA -> slideIntoContainer(sentido(voltando), movimento(DURACAO_ABA)) { it / 12 } + aparece(DURACAO_ABA)
        TipoTransicao.PAGINA -> slideIntoContainer(sentido(voltando), movimento()) { it / 5 } + aparece()
        // Ao fechar a folha, a tela de baixo só reaparece: quem se move é a folha
        TipoTransicao.FOLHA -> if (voltando) aparece(DURACAO_FECHAR) else slideInVertically(movimento()) { it / 8 } + aparece()
    }

fun EscopoTransicao.saidaDaTela(voltando: Boolean): ExitTransition =
    when (tipoTransicao(origem, destino, voltando)) {
        TipoTransicao.SUAVE -> some()
        TipoTransicao.ABA -> slideOutOfContainer(sentido(voltando), movimento(DURACAO_ABA)) { it / 12 } + some()
        TipoTransicao.PAGINA -> slideOutOfContainer(sentido(voltando), movimento()) { it / 10 } + some()
        TipoTransicao.FOLHA ->
            if (voltando) {
                slideOutVertically(movimento(DURACAO_FECHAR)) { it / 8 } + fadeOut(movimento(DURACAO_FECHAR - APARECE_DEPOIS))
            } else {
                some()
            }
    }
