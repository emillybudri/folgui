package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes dos atalhos da tela inicial e do evento sem fim
class AtalhosTest {

    // Uma segunda-feira: terça é trabalho e sábado é a próxima folga
    private val segunda = hoje().let { it + Math.floorMod(1 - diaDaSemana(it), 7) }
    private val voce = Pessoa(Pessoa.ID_VOCE, "Eu", TipoEscala.SEMANAL, segunda, padrao = TipoEscala.SEMANAL.padraoCom(Turno(6 * 60, 14 * 60)))
    private val bia = Pessoa(1, "Bia", TipoEscala.SEMANAL, segunda)
    private val encontro = Encontro(segunda + 5, Janela(13 * 60, 120))

    private fun tipos(pessoas: List<Pessoa> = emptyList(), eventos: List<Evento> = emptyList(), feriados: Map<Int, String> = emptyMap(), e: Encontro? = null) =
        atalhosDoInicio(voce, pessoas, eventos, feriados, e, segunda).map { it.tipo }

    @Test
    fun `sozinha aprende a chamar um amigo`() {
        assertEquals(TipoAtalho.CHAMAR_AMIGO, tipos().first())
    }

    @Test
    fun `com amigos mostra o encontro primeiro`() {
        assertEquals(TipoAtalho.ENCONTRO, tipos(listOf(bia), e = encontro).first())
    }

    @Test
    fun `folga vazia sugere planejar e folga com evento nao`() {
        assertTrue(TipoAtalho.PLANEJAR_FOLGA in tipos())
        val evento = Evento(titulo = "Praia", dia = segunda + 5, horario = "09:00", local = "", antecedenciaMin = -1)
        assertTrue(TipoAtalho.VER_FOLGA in tipos(eventos = listOf(evento)))
    }

    @Test
    fun `amanha cedo diz a hora de acordar`() {
        val atalho = atalhosDoInicio(voce, emptyList(), emptyList(), emptyMap(), null, segunda).first { it.tipo == TipoAtalho.AMANHA }
        assertEquals("Amanhã às 6h", atalho.titulo)
        assertEquals("Acorde às 5h", atalho.texto)
    }

    @Test
    fun `nunca passa de quatro atalhos`() {
        val muitos = tipos(listOf(bia), feriados = mapOf(segunda + 3 to "Natal"), e = encontro)
        assertEquals(4, muitos.size)
    }

    @Test
    fun `sono nao e atalho`() {
        assertFalse(tipos().any { it.name.contains("SONO") })
    }

    private fun evento(duracao: Int) =
        Evento(titulo = "Dentista", dia = segunda, horario = "15:00", local = "", antecedenciaMin = -1, duracaoMin = duracao)

    @Test
    fun `evento sem fim mostra so o inicio`() {
        assertEquals("15:00", evento(SEM_FIM).faixaHorario)
        assertEquals("15:00 às 16:00", evento(60).faixaHorario)
    }

    @Test
    fun `evento sem fim ainda segura uma hora da agenda`() {
        assertEquals(60, evento(SEM_FIM).janela.duracao)
        assertEquals(1, eventosEmConflito(listOf(evento(SEM_FIM).copy(id = 1)), segunda, Janela(15 * 60 + 30, 30)).size)
    }
}
