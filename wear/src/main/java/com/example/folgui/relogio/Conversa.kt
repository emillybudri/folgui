package com.example.folgui.relogio

import org.json.JSONObject

// ============================================================================
// O que o relógio mostra e como ele entende o que o celular responde
// ============================================================================

// Caminhos das mensagens. Os mesmos nomes estão em ConversaRelogio.kt, no app do celular
const val CAPACIDADE_CELULAR = "folgui_celular"
const val CAMINHO_PEDIDO = "/folgui/pedido"
const val CAMINHO_CONFIRMAR = "/folgui/confirmar"
const val CAMINHO_CANCELAR = "/folgui/cancelar"
const val CAMINHO_RESPOSTA = "/folgui/resposta"

/** Cada estado é uma tela do relógio. */
sealed interface Tela {
    data object Inicio : Tela
    data object Pensando : Tela

    /** A IA entendeu o pedido e pergunta se é isso mesmo. */
    data class Confirmar(val resumo: String, val avisos: List<String>) : Tela

    /** A IA precisa de mais informação ou só respondeu. */
    data class Pergunta(val texto: String) : Tela
    data class Marcado(val texto: String) : Tela
    data class Erro(val texto: String) : Tela
}

/** Transforma o JSON que veio do celular na próxima tela. */
fun telaDaResposta(json: String): Tela = try {
    val resposta = JSONObject(json)
    val texto = resposta.getString("texto")
    when (resposta.getString("tipo")) {
        "confirmar" -> {
            val lista = resposta.optJSONArray("avisos")
            val avisos = List(lista?.length() ?: 0) { lista!!.getString(it) }
            Tela.Confirmar(texto, avisos)
        }
        "pergunta" -> Tela.Pergunta(texto)
        "marcado" -> Tela.Marcado(texto)
        else -> Tela.Erro(texto)
    }
} catch (e: Exception) {
    Tela.Erro("Não entendi a resposta do celular.")
}

/** O que o relógio fala em voz alta ao abrir cada tela (null = fica em silêncio). */
fun falaDaTela(tela: Tela): String? = when (tela) {
    is Tela.Confirmar -> "${tela.resumo}. É isso mesmo?"
    is Tela.Pergunta -> tela.texto
    is Tela.Marcado -> "Pronto, marcado."
    else -> null
}
