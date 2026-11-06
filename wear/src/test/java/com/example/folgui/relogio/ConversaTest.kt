package com.example.folgui.relogio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Testes de como o relógio entende o que o celular manda
class ConversaTest {

    @Test
    fun `resposta de confirmar traz o resumo e os avisos`() {
        val tela = telaDaResposta("""{"tipo":"confirmar","texto":"Almoço","avisos":["Você estará trabalhando"]}""")
        assertEquals(Tela.Confirmar("Almoço", listOf("Você estará trabalhando")), tela)
    }

    @Test
    fun `confirmar sem avisos vira lista vazia`() {
        assertEquals(Tela.Confirmar("Almoço", emptyList()), telaDaResposta("""{"tipo":"confirmar","texto":"Almoço"}"""))
    }

    @Test
    fun `pergunta e marcado abrem a tela certa`() {
        assertEquals(Tela.Pergunta("Que horas?"), telaDaResposta("""{"tipo":"pergunta","texto":"Que horas?"}"""))
        assertEquals(Tela.Marcado("Almoço"), telaDaResposta("""{"tipo":"marcado","texto":"Almoço"}"""))
    }

    @Test
    fun `erro mostra o texto que veio do celular`() {
        assertEquals(Tela.Erro("Sem internet"), telaDaResposta("""{"tipo":"erro","texto":"Sem internet"}"""))
    }

    @Test
    fun `mensagem quebrada nao derruba o app`() {
        assertEquals(Tela.Erro("Não entendi a resposta do celular."), telaDaResposta("isso não é json"))
    }

    @Test
    fun `o relogio fala a pergunta de confirmacao`() {
        assertEquals("Almoço. É isso mesmo?", falaDaTela(Tela.Confirmar("Almoço", emptyList())))
        assertNull(falaDaTela(Tela.Pensando))
    }
}
