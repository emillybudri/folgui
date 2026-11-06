package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes do sono que combina com o trabalho e dos feriados na tela inicial
class RotinaTest {

    private fun hora(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun `quem entra as 6 acorda antes`() {
        assertEquals(Turno(hora(21), hora(5)), sonoSugerido(listOf(Turno(hora(6), hora(18)))))
    }

    @Test
    fun `quem entra tarde mantem o horario comum`() {
        assertEquals(SONO_PADRAO, sonoSugerido(listOf(Turno(hora(9), hora(18)))))
    }

    @Test
    fun `sem trabalho usa o horario comum`() {
        assertEquals(SONO_PADRAO, sonoSugerido(emptyList()))
    }

    @Test
    fun `turno da noite dorme depois de sair`() {
        assertEquals(Turno(hora(8), hora(16)), sonoSugerido(listOf(Turno(hora(19), hora(7)))))
    }

    @Test
    fun `quem sai tarde dorme depois do turno`() {
        val sono = sonoSugerido(listOf(Turno(hora(15), hora(23, 30))))
        assertEquals(Turno(hora(0, 30), hora(8, 30)), sono)
    }

    @Test
    fun `plantao de 24h acorda antes de entrar`() {
        assertEquals(Turno(hora(22), hora(6)), sonoSugerido(listOf(Turno(hora(7), hora(7)))))
    }

    @Test
    fun `avisa quando acordaria depois de entrar`() {
        assertEquals(
            "Você entra às 6h, mas acordaria às 7h.",
            avisoSono(Turno(hora(23), hora(7)), listOf(Turno(hora(6), hora(18))))
        )
    }

    @Test
    fun `avisa quando acordaria em cima da hora`() {
        assertNotNull(avisoSono(Turno(hora(22), hora(5, 50)), listOf(Turno(hora(6), hora(18)))))
    }

    @Test
    fun `a sugestao nunca gera aviso`() {
        val horarios = listOf(
            Turno(hora(6), hora(18)), Turno(hora(8), hora(17)), Turno(hora(14), hora(22)),
            Turno(hora(19), hora(7)), Turno(hora(22), hora(6)), Turno(hora(15), hora(23, 30))
        )
        horarios.forEach { turno -> assertNull(turno.resumo, avisoSono(sonoSugerido(listOf(turno)), listOf(turno))) }
    }

    @Test
    fun `sono que cabe na rotina nao avisa`() {
        assertNull(avisoSono(Turno(hora(23), hora(7)), listOf(Turno(hora(9), hora(18)))))
    }

    // Segunda a sexta. O dia 0 de referência é uma segunda
    private val segunda = hoje().let { it + Math.floorMod(1 - diaDaSemana(it), 7) }
    private val voce = Pessoa(Pessoa.ID_VOCE, "Eu", TipoEscala.SEMANAL, segunda)

    @Test
    fun `feriado distante nao aparece no inicio`() {
        assertNull(voce.feriadoParaPerguntar(setOf(segunda + 14), diaHoje = segunda))
    }

    @Test
    fun `feriado em ate uma semana aparece no inicio`() {
        assertEquals(segunda + 7, voce.feriadoParaPerguntar(setOf(segunda + 7), diaHoje = segunda))
    }

    @Test
    fun `quem nao folga em feriados nunca recebe a pergunta`() {
        val semFolga = voce.copy(folgaFeriados = false)
        assertFalse(semFolga.perguntarFeriado(segunda, segunda))
        assertNull(semFolga.feriadoParaPerguntar(setOf(segunda), diaHoje = segunda))
    }

    @Test
    fun `feriado em dia de folga nao precisa de pergunta`() {
        assertFalse(voce.perguntarFeriado(segunda + 5, segunda))
        assertTrue(voce.perguntarFeriado(segunda, segunda))
    }
}
