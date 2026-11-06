package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes da gravação na Google Agenda: qual agenda recebe o evento e como evitar eventos repetidos
class AgendaTest {

    private fun compromisso(eventoId: Long) = CompromissoAgenda("Algo", 0, 1, false, "Agenda", eventoId)

    private fun evento(id: Int, agendaId: Long) =
        Evento(id = id, titulo = "Almoço", dia = hoje(), horario = "12:00", local = "", antecedenciaMin = -1, agendaId = agendaId)

    @Test
    fun `prefere a agenda principal do Google`() {
        val agendas = listOf(
            AgendaDoCelular(1, principal = true, google = false),
            AgendaDoCelular(2, principal = false, google = true),
            AgendaDoCelular(3, principal = true, google = true)
        )
        assertEquals(3L, escolherAgenda(agendas)?.id)
    }

    @Test
    fun `sem a principal usa outra do Google antes da agenda do aparelho`() {
        val agendas = listOf(
            AgendaDoCelular(1, principal = true, google = false),
            AgendaDoCelular(2, principal = false, google = true)
        )
        assertEquals(2L, escolherAgenda(agendas)?.id)
    }

    @Test
    fun `sem nenhuma agenda nao escolhe nada`() {
        assertNull(escolherAgenda(emptyList()))
    }

    @Test
    fun `evento do Folgui nao aparece de novo como compromisso da agenda`() {
        val agenda = mapOf(10 to listOf(compromisso(77), compromisso(88)))
        val resultado = semEventosDoFolgui(agenda, listOf(evento(1, agendaId = 77)))
        assertEquals(listOf(88L), resultado[10]?.map { it.eventoId })
    }

    @Test
    fun `dia que so tinha evento do Folgui some da leitura da agenda`() {
        val agenda = mapOf(10 to listOf(compromisso(77)))
        assertTrue(semEventosDoFolgui(agenda, listOf(evento(1, agendaId = 77))).isEmpty())
    }

    @Test
    fun `evento fora da agenda nao esconde compromisso nenhum`() {
        val agenda = mapOf(10 to listOf(compromisso(0), compromisso(5)))
        val resultado = semEventosDoFolgui(agenda, listOf(evento(1, agendaId = 0)))
        assertEquals(2, resultado[10]?.size)
    }
}
