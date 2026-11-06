package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes dos melhores dias para um encontro e da pergunta nos feriados
class EncontroTest {

    private val segunda = diaDe(2026, 11, 2)
    private val sabado = diaDe(2026, 11, 7)

    private fun semanal(id: Int, turno: Turno) = Pessoa(
        id = id,
        nome = "Pessoa $id",
        escala = TipoEscala.SEMANAL,
        inicioCiclo = segunda,
        padrao = listOf(null, turno, turno, turno, turno, turno, null)
    )

    // Segunda a sexta: você das 8h às 17h, o amigo das 8h às 22h
    private val voce = semanal(Pessoa.ID_VOCE, Turno(480, 1020))
    private val amigo = semanal(1, Turno(480, 1320))

    @Test
    fun `dia em que o amigo trabalha nao aparece como melhor dia`() {
        val noite = Janela(20 * 60, 120)
        val folguista = semanal(2, Turno(480, 1020)).copy(padrao = List(7) { null })
        val comercial = semanal(1, Turno(480, 1020))
        // O horário das 20h está livre, e mesmo assim o dia não é o melhor: o amigo trabalha
        assertTrue(todosLivres(listOf(folguista, comercial), segunda, noite))
        assertEquals(QualidadeDia.PARTE, qualidadeDia(listOf(folguista, comercial), segunda, diaHoje = segunda - 1))
    }

    @Test
    fun `folga de todos e o melhor dia`() {
        assertEquals(QualidadeDia.MELHOR, qualidadeDia(listOf(voce, amigo), sabado, diaHoje = segunda))
    }

    @Test
    fun `sem tempo juntos o dia nao serve`() {
        // Das 7h às 8h e das 22h à meia-noite não cabem 3 horas
        assertEquals(QualidadeDia.NAO_DA, qualidadeDia(listOf(voce, amigo), segunda, duracao = 180, diaHoje = segunda - 1))
    }

    @Test
    fun `hoje so conta o que ainda nao passou`() {
        val folga = semanal(2, Turno(480, 1020)).copy(padrao = List(7) { null })
        assertEquals(QualidadeDia.MELHOR, qualidadeDia(listOf(folga), segunda, diaHoje = segunda, minutoHoje = 600))
        assertEquals(QualidadeDia.NAO_DA, qualidadeDia(listOf(folga), segunda, diaHoje = segunda, minutoHoje = 1430))
    }

    @Test
    fun `melhores dias vem antes dos dias de trabalho`() {
        val dias = melhoresDias(listOf(voce, amigo), duracao = 60, quantidade = 2, diaHoje = segunda, minutoHoje = 0)
        assertEquals(listOf(sabado, sabado + 1), dias)
    }

    @Test
    fun `sem folga em comum sugere os dias que dao em parte`() {
        val semFolga = semanal(1, Turno(480, 1020)).copy(padrao = List(7) { Turno(480, 1020) })
        val dias = melhoresDias(listOf(semFolga), duracao = 60, quantidade = 1, diaHoje = segunda, minutoHoje = 0)
        assertEquals(listOf(segunda), dias)
    }

    @Test
    fun `sem nenhum horario a lista fica vazia`() {
        val sempre = semanal(1, Turno(0, 1439)).copy(padrao = List(7) { Turno(0, 1439) })
        assertTrue(melhoresDias(listOf(sempre), diaHoje = segunda, minutoHoje = 0).isEmpty())
    }

    @Test
    fun `feriado sem resposta pergunta e com resposta nao`() {
        assertTrue(voce.perguntarFeriado(segunda, diaHoje = segunda))
        val respondeu = voce.copy(excecoes = mapOf(segunda to voce.respostaFeriado(segunda, trabalha = false)))
        assertFalse(respondeu.perguntarFeriado(segunda, diaHoje = segunda))
    }

    @Test
    fun `nao pergunta sobre feriado que passou ou nas ferias`() {
        assertFalse(voce.perguntarFeriado(segunda, diaHoje = segunda + 1))
        val ferias = voce.copy(feriasInicio = segunda, feriasFim = segunda + 5)
        assertFalse(ferias.perguntarFeriado(segunda, diaHoje = segunda))
    }

    @Test
    fun `nao trabalhar no feriado vira folga mesmo com a escala dizendo trabalho`() {
        val respondeu = voce.copy(excecoes = mapOf(segunda to voce.respostaFeriado(segunda, trabalha = false)))
        assertEquals(StatusDia.FOLGA, respondeu.status(segunda))
    }

    @Test
    fun `trabalhar no feriado mantem o turno da escala`() {
        val resposta = voce.respostaFeriado(segunda, trabalha = true)
        assertEquals(Turno(480, 1020), resposta.turno)
        assertEquals(TipoExcecao.FERIADO, resposta.tipo)
    }

    @Test
    fun `trabalhar em feriado que seria folga usa o turno de sempre`() {
        val resposta = voce.respostaFeriado(sabado, trabalha = true)
        assertEquals(Turno(480, 1020), resposta.turno)
        val respondeu = voce.copy(excecoes = mapOf(sabado to resposta))
        assertEquals(StatusDia.TRABALHO, respondeu.status(sabado))
        assertNull(voce.turnoNoDia(sabado))
    }
}
