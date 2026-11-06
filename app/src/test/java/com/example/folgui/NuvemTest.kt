package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Testes da cópia na conta: o que sai de um aparelho precisa voltar igual no outro
class NuvemTest {

    private val dia = diaDe(2026, 11, 2)

    private val perfil = Perfil(
        nome = "Ana",
        escala = TipoEscala.ESCALA_12X36,
        inicioCiclo = dia,
        feriasInicio = dia + 30,
        feriasFim = dia + 40,
        padrao = listOf(Turno(420, 1140), null),
        descansoMin = 60,
        sono = Turno(1380, 420)
    )

    private val folga = Excecao(dia + 1, TipoExcecao.FOLGA, null)
    private val extra = Excecao(dia + 2, TipoExcecao.TRABALHO, Turno(480, 720))

    private val dados = DadosNuvem(
        perfil = perfil,
        excecoesVoce = mapOf(folga.dia to folga, extra.dia to extra),
        pessoas = listOf(
            Pessoa(
                id = 7,
                nome = "Tia Márcia \"Má\"",
                escala = TipoEscala.SEMANAL,
                inicioCiclo = dia,
                padrao = listOf(Turno(480, 1020), Turno(480, 1020), null),
                email = "marcia@gmail.com",
                excecoes = mapOf(folga.dia to folga)
            )
        ),
        eventos = listOf(
            Evento(id = 3, titulo = "Almoço", dia = dia + 5, horario = "12:30", local = "Centro", antecedenciaMin = 60, duracaoMin = 90, participantes = setOf(7)),
            Evento(id = 4, titulo = "Dentista", dia = dia + 6, horario = "09:00", local = "", antecedenciaMin = -1, participantes = null)
        )
    )

    @Test
    fun `dados voltam iguais depois de ir para a conta`() {
        assertEquals(dados, textoParaDados(dadosParaTexto(dados)))
    }

    @Test
    fun `id da Google Agenda nao vai para outro aparelho`() {
        val comAgenda = dados.copy(eventos = dados.eventos.map { it.copy(agendaId = 99) })
        val volta = textoParaDados(dadosParaTexto(comAgenda))!!
        assertEquals(listOf(0L, 0L), volta.eventos.map { it.agendaId })
    }

    @Test
    fun `mesmos dados geram o mesmo texto para nao reenviar a toa`() {
        assertEquals(dadosParaTexto(dados), dadosParaTexto(textoParaDados(dadosParaTexto(dados))!!))
    }

    @Test
    fun `texto corrompido nao vira dados`() {
        assertNull(textoParaDados("{}"))
        assertNull(textoParaDados("não é json"))
    }

    @Test
    fun `liga evento restaurado ao mesmo compromisso da agenda`() {
        val evento = dados.eventos[0]
        val igual = CompromissoAgenda(evento.titulo, evento.momento, evento.momento + 1, false, "Agenda", 55)
        val outro = CompromissoAgenda("Outro", evento.momento, evento.momento + 1, false, "Agenda", 56)
        assertEquals(mapOf(3 to 55L), vinculosAgenda(dados.eventos, mapOf(evento.dia to listOf(outro, igual))))
    }

    @Test
    fun `dois eventos iguais nao ficam ligados ao mesmo compromisso`() {
        val evento = dados.eventos[0]
        val repetido = evento.copy(id = 9)
        val compromisso = CompromissoAgenda(evento.titulo, evento.momento, evento.momento + 1, false, "Agenda", 55)
        assertEquals(1, vinculosAgenda(listOf(evento, repetido), mapOf(evento.dia to listOf(compromisso))).size)
    }

    @Test
    fun `evento ja ligado continua como esta`() {
        val evento = dados.eventos[0].copy(agendaId = 55)
        val compromisso = CompromissoAgenda(evento.titulo, evento.momento, evento.momento + 1, false, "Agenda", 55)
        assertEquals(emptyMap<Int, Long>(), vinculosAgenda(listOf(evento), mapOf(evento.dia to listOf(compromisso))))
    }
}
