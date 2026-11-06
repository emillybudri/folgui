package com.example.folgui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes da conversa com o relógio: nada é marcado sem um "sim" válido
class RelogioTest {

    private val criar = AcaoIA(tipo = TipoAcaoIA.CRIAR_EVENTO, titulo = "Almoço", dia = hoje() + 1, horario = "13:00")
    private val ferias = AcaoIA(tipo = TipoAcaoIA.REMOVER_FERIAS)

    private fun mensagem(vararg acoes: AcaoIA, texto: String = "Certo") = MensagemIA(
        deUsuario = false,
        texto = texto,
        acoes = acoes.toList(),
        descricoes = acoes.map { "Novo evento \"${it.titulo}\"" }
    )

    @Test
    fun `evento pede confirmacao com os avisos`() {
        val resposta = respostaParaRelogio(mensagem(criar).copy(avisos = listOf("Você estará trabalhando nesse horário.")))
        assertEquals(TipoRespostaRelogio.CONFIRMAR, resposta.tipo)
        assertEquals("Novo evento \"Almoço\"", resposta.texto)
        assertEquals(listOf("Você estará trabalhando nesse horário."), resposta.avisos)
    }

    @Test
    fun `sem acao a IA esta perguntando algo`() {
        val resposta = respostaParaRelogio(mensagem(texto = "Que horas?"))
        assertEquals(RespostaRelogio(TipoRespostaRelogio.PERGUNTA, "Que horas?"), resposta)
    }

    @Test
    fun `relogio recusa mudar outras coisas`() {
        assertEquals(TipoRespostaRelogio.ERRO, respostaParaRelogio(mensagem(criar, ferias)).tipo)
    }

    @Test
    fun `proposta com outras mudancas nao pode ser confirmada`() {
        val conversa = ConversaRelogio()
        conversa.registrar("marque e tire as férias", mensagem(criar, ferias), agora = 1000)
        assertTrue(conversa.retirarProposta(agora = 2000).isEmpty())
    }

    @Test
    fun `sim marca uma vez so`() {
        val conversa = ConversaRelogio()
        conversa.registrar("marque almoço", mensagem(criar), agora = 1000)
        assertEquals(listOf(criar), conversa.retirarProposta(agora = 2000))
        assertTrue(conversa.retirarProposta(agora = 2100).isEmpty())
    }

    @Test
    fun `sim atrasado nao marca`() {
        val conversa = ConversaRelogio()
        conversa.registrar("marque almoço", mensagem(criar), agora = 1000)
        assertTrue(conversa.retirarProposta(agora = 1000 + VALIDADE_PROPOSTA_MS + 1).isEmpty())
    }

    @Test
    fun `nao cancela o pedido`() {
        val conversa = ConversaRelogio()
        conversa.registrar("marque almoço", mensagem(criar), agora = 1000)
        conversa.esquecer()
        assertTrue(conversa.retirarProposta(agora = 2000).isEmpty())
        assertTrue(conversa.historico(agora = 2000).isEmpty())
    }

    @Test
    fun `resposta da pergunta continua a mesma conversa`() {
        val conversa = ConversaRelogio()
        conversa.registrar("marque almoço", mensagem(texto = "Que horas?"), agora = 1000)
        assertEquals(listOf("marque almoço", "Que horas?"), conversa.historico(agora = 2000).map { it.texto })
    }

    @Test
    fun `conversa antiga nao entra no pedido novo`() {
        val conversa = ConversaRelogio()
        conversa.registrar("marque almoço", mensagem(texto = "Que horas?"), agora = 1000)
        assertTrue(conversa.historico(agora = 1000 + VALIDADE_PROPOSTA_MS + 1).isEmpty())
    }

    @Test
    fun `resposta vira o json que o relogio espera`() {
        val json = JSONObject(respostaParaJson(RespostaRelogio(TipoRespostaRelogio.CONFIRMAR, "Almoço", listOf("Aviso"))))
        assertEquals("confirmar", json.getString("tipo"))
        assertEquals("Almoço", json.getString("texto"))
        assertEquals("Aviso", json.getJSONArray("avisos").getString(0))
    }
}
