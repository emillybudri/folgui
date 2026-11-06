package com.example.folgui

import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

// ============================================================================
// Conversa com o relógio: regras sem Android, para poderem ser testadas
// ============================================================================

// Caminhos das mensagens. Os mesmos nomes estão em Conversa.kt, no módulo wear
const val CAMINHO_PEDIDO = "/folgui/pedido"
const val CAMINHO_CONFIRMAR = "/folgui/confirmar"
const val CAMINHO_CANCELAR = "/folgui/cancelar"
const val CAMINHO_RESPOSTA = "/folgui/resposta"

/** Depois deste tempo, um "sim" não vale mais: a pessoa precisa pedir de novo. */
const val VALIDADE_PROPOSTA_MS = 2 * 60_000L

/** Muda quando o relógio marca algo, para o app aberto no celular recarregar a lista. */
val mudancasDoRelogio = MutableStateFlow(0)

enum class TipoRespostaRelogio(val codigo: String) {
    CONFIRMAR("confirmar"),
    PERGUNTA("pergunta"),
    MARCADO("marcado"),
    ERRO("erro")
}

data class RespostaRelogio(
    val tipo: TipoRespostaRelogio,
    val texto: String,
    val avisos: List<String> = emptyList()
)

fun respostaParaJson(resposta: RespostaRelogio): String = JSONObject()
    .put("tipo", resposta.tipo.codigo)
    .put("texto", resposta.texto)
    .put("avisos", JSONArray(resposta.avisos))
    .toString()

/**
 * Decide o que o relógio mostra. Ele só marca compromissos: mudar férias ou escala
 * mexe em muita coisa para confirmar em uma tela tão pequena.
 */
fun respostaParaRelogio(mensagem: MensagemIA): RespostaRelogio = when {
    mensagem.acoes.isEmpty() -> RespostaRelogio(TipoRespostaRelogio.PERGUNTA, mensagem.texto)
    mensagem.acoes.any { it.tipo != TipoAcaoIA.CRIAR_EVENTO } -> RespostaRelogio(
        TipoRespostaRelogio.ERRO,
        "Pelo relógio eu só marco compromissos. Para mudar outras coisas, use o celular."
    )
    else -> RespostaRelogio(
        TipoRespostaRelogio.CONFIRMAR,
        mensagem.descricoes.joinToString("\n"),
        mensagem.avisos
    )
}

/** Guarda a conversa entre o pedido e o "sim", que chegam em mensagens separadas. */
class ConversaRelogio {
    private var historico = emptyList<MensagemIA>()
    private var proposta = emptyList<AcaoIA>()
    private var atualizadaEm = 0L

    /** O que já foi dito, para a IA entender uma resposta como "às oito". */
    fun historico(agora: Long): List<MensagemIA> {
        if (expirou(agora)) esquecer()
        return historico
    }

    fun registrar(pedido: String, resposta: MensagemIA, agora: Long) {
        historico = historico + MensagemIA(deUsuario = true, texto = pedido) + resposta
        val soEventos = resposta.acoes.all { it.tipo == TipoAcaoIA.CRIAR_EVENTO }
        proposta = if (soEventos) resposta.acoes else emptyList()
        atualizadaEm = agora
    }

    /** Entrega a proposta uma única vez: um "sim" repetido não marca o evento duas vezes. */
    fun retirarProposta(agora: Long): List<AcaoIA> {
        val acoes = if (expirou(agora)) emptyList() else proposta
        esquecer()
        return acoes
    }

    fun esquecer() {
        historico = emptyList()
        proposta = emptyList()
    }

    private fun expirou(agora: Long) = agora - atualizadaEm > VALIDADE_PROPOSTA_MS
}
