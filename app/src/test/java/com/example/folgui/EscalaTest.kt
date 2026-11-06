package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes das escalas: todos os tipos, turnos de dia e de noite, descanso, exceções e férias
class EscalaTest {

    private val segunda = diaDe(2026, 11, 2)
    private val sabado = diaDe(2026, 11, 7)

    private fun hora(h: Int) = h * 60

    private fun pessoa(
        escala: TipoEscala,
        turno: Turno = escala.turnoPadrao,
        inicio: Int = segunda,
        descanso: Int = 0,
        id: Int = 1
    ) = Pessoa(id, "P$id", escala, inicio, escala.padraoCom(turno), descansoMin = descanso)

    // ---------- Caso real: tia das 6h às 18h e padrasto das 18h às 6h, folgando juntos ----------

    private val tia = pessoa(TipoEscala.ESCALA_12X36, Turno(hora(6), hora(18)), id = 1)
    private val padrasto = pessoa(TipoEscala.ESCALA_12X36, Turno(hora(18), hora(6)), id = 2)

    @Test
    fun tiaEPadrastoFolgamNoMesmoDia() {
        assertEquals(StatusDia.TRABALHO, tia.status(segunda))
        assertEquals(StatusDia.TRABALHO, padrasto.status(segunda))
        assertEquals(StatusDia.FOLGA, tia.status(segunda + 1))
        assertEquals(StatusDia.FOLGA, padrasto.status(segunda + 1))
        assertEquals(SituacaoGrupo.TODOS_LIVRES, situacaoGrupo(listOf(tia, padrasto), segunda + 1))
    }

    @Test
    fun eventoNaNoiteDoPlantaoNoturno() {
        val noite = Janela(hora(20), 120)
        assertTrue(tia.livre(segunda, noite))
        assertFalse(padrasto.livre(segunda, noite))
        assertEquals(SituacaoGrupo.PARCIAL, situacaoGrupo(listOf(tia, padrasto), segunda, noite))
        // Mesmo horário no dia seguinte: os dois livres
        assertTrue(todosLivres(listOf(tia, padrasto), segunda + 1, noite))
    }

    @Test
    fun plantaoNoturnoTerminaNoDiaSeguinte() {
        assertFalse(padrasto.livre(segunda + 1, Janela(hora(5), 60)))
        assertTrue(padrasto.livre(segunda + 1, Janela(hora(6), 60)))
        assertEquals(listOf(0 to hora(6)), padrasto.periodosNoDia(segunda + 1))
        assertEquals(listOf(hora(18) to MINUTOS_DIA), padrasto.periodosNoDia(segunda))
        assertEquals("Folga · sai do plantão às 6h", padrasto.descricaoDia(segunda + 1))
        assertEquals("Trabalha das 18h às 6h do dia seguinte", padrasto.descricaoDia(segunda))
    }

    @Test
    fun horariosEmComumDoCasal() {
        // No dia de plantão não sobra horário entre 7h e meia-noite
        assertTrue(janelasLivres(listOf(tia, padrasto), segunda).isEmpty())
        // No dia de folga, os dois estão livres das 7h à meia-noite
        assertEquals(listOf(Janela(hora(7), hora(17))), janelasLivres(listOf(tia, padrasto), segunda + 1))
    }

    @Test
    fun descansoDepoisDoPlantaoNoturno() {
        val comDescanso = padrasto.copy(descansoMin = hora(6))
        assertFalse(comDescanso.livre(segunda + 1, Janela(hora(10), 60)))
        assertTrue(comDescanso.livre(segunda + 1, Janela(hora(12), 60)))
        assertEquals(listOf(hora(6) to hora(12)), comDescanso.descansoNoDia(segunda + 1))
        assertEquals("Folga · sai do plantão às 6h e descansa até 12h", comDescanso.descricaoDia(segunda + 1))
        // O descanso não vale para turnos de dia
        assertTrue(tia.copy(descansoMin = hora(6)).livre(segunda, Janela(hora(19), 60)))
    }

    // ---------- Cada tipo de escala ----------

    @Test
    fun semanalSegundaASexta() {
        val p = pessoa(TipoEscala.SEMANAL)
        (0..4).forEach { assertEquals(StatusDia.TRABALHO, p.status(segunda + it)) }
        assertEquals(StatusDia.FOLGA, p.status(sabado))
        assertEquals(StatusDia.FOLGA, p.status(sabado + 1))
        // Depois do expediente está livre
        assertTrue(p.livre(segunda, Janela(hora(18), 120)))
        assertFalse(p.livre(segunda, Janela(hora(16), 120)))
        assertEquals("Seg a sex · 8h às 17h", p.resumoEscala)
    }

    @Test
    fun semanalComSabadoMeioPeriodo() {
        val semana = Turno(hora(8), hora(17))
        val sabadoManha = Turno(hora(8), hora(12))
        val padrao = List(7) { d -> if (d == 0) null else if (d == 6) sabadoManha else semana }
        val p = Pessoa(1, "P", TipoEscala.SEMANAL, 0, padrao)
        assertEquals(StatusDia.TRABALHO, p.status(sabado))
        assertTrue(p.livre(sabado, Janela(hora(13), 120)))
        assertFalse(p.livre(sabado, Janela(hora(11), 60)))
        assertEquals("Seg a sáb · 8h às 17h e 8h às 12h", p.resumoEscala)
    }

    @Test
    fun escalasDeRevezamento() {
        listOf(
            TipoEscala.ESCALA_12X36, TipoEscala.ESCALA_24X48, TipoEscala.ESCALA_2X2,
            TipoEscala.ESCALA_4X2, TipoEscala.ESCALA_5X1, TipoEscala.ESCALA_6X1
        ).forEach { tipo ->
            val p = pessoa(tipo)
            val ciclo = tipo.diasTrabalho + tipo.diasFolga
            val trabalhados = (0 until ciclo * 10).count { p.status(segunda + it) == StatusDia.TRABALHO }
            assertEquals(tipo.codigo, tipo.diasTrabalho * 10, trabalhados)
            // O ciclo começa com trabalho e termina com folga
            assertEquals(tipo.codigo, StatusDia.TRABALHO, p.status(segunda))
            assertEquals(tipo.codigo, StatusDia.FOLGA, p.status(segunda + ciclo - 1))
            // Dias antes do início também seguem a escala
            assertEquals(tipo.codigo, p.status(segunda), p.status(segunda - ciclo * 3))
        }
    }

    @Test
    fun plantaoDe24Horas() {
        val p = pessoa(TipoEscala.ESCALA_24X48)
        assertEquals(MINUTOS_DIA, p.padrao[0]!!.duracao)
        assertFalse(p.livre(segunda + 1, Janela(hora(6), 60)))
        assertTrue(p.livre(segunda + 1, Janela(hora(7), 60)))
        assertTrue(p.livre(segunda + 2, Janela(0, MINUTOS_DIA)))
        assertEquals(listOf(0 to hora(7)), p.periodosNoDia(segunda + 1))
    }

    @Test
    fun turnoNoturnoEmEscalaSemanal() {
        val p = pessoa(TipoEscala.ESCALA_4X2, Turno(hora(22), hora(6)))
        assertTrue(p.turnoNoturno)
        // Quarto plantão termina na madrugada do primeiro dia de folga
        assertFalse(p.livre(segunda + 4, Janela(hora(5), 30)))
        assertTrue(p.livre(segunda + 4, Janela(hora(8), 60)))
        assertEquals(StatusDia.FOLGA, p.status(segunda + 4))
    }

    @Test
    fun personalizadaDiaENoite() {
        val m = MODELOS_PERSONALIZADOS.first()
        val padrao = m.dias.map { c -> if (c == 'A') m.turnoA else if (c == 'B') m.turnoB else null }
        val p = Pessoa(1, "P", TipoEscala.PERSONALIZADA, segunda, padrao)
        assertEquals(8, p.padrao.size)
        assertEquals(StatusDia.TRABALHO, p.status(segunda + 3))
        assertTrue(p.turnoNoDia(segunda + 3)!!.noturno)
        // Depois da segunda noite, folga mas ocupado até 7h
        assertEquals(StatusDia.FOLGA, p.status(segunda + 4))
        assertFalse(p.livre(segunda + 4, Janela(hora(6), 30)))
        assertEquals(StatusDia.FOLGA, p.status(segunda + 7))
        assertEquals(StatusDia.TRABALHO, p.status(segunda + 8))
        assertEquals("Ciclo de 8 dias · 7h às 19h e 19h às 7h", p.resumoEscala)
    }

    // ---------- Exceções e férias ----------

    @Test
    fun excecoesMudamODia() {
        val folga = Excecao(segunda, TipoExcecao.FOLGA, null)
        val extra = Excecao(segunda + 1, TipoExcecao.TRABALHO, Turno(hora(8), hora(14)))
        val p = tia.copy(excecoes = mapOf(segunda to folga, segunda + 1 to extra))
        assertEquals(StatusDia.FOLGA, p.status(segunda))
        assertEquals(StatusDia.TRABALHO, p.status(segunda + 1))
        assertFalse(p.livre(segunda + 1, Janela(hora(9), 60)))
        assertTrue(p.livre(segunda + 1, Janela(hora(15), 60)))
        assertEquals("Folga extra", p.descricaoDia(segunda))
        assertEquals("Trabalho extra · 8h às 14h", p.descricaoDia(segunda + 1))
        // A escala original continua a mesma
        assertEquals(StatusDia.TRABALHO, tia.status(segunda))
    }

    @Test
    fun feriasValemMaisQueAEscalaEAsExcecoes() {
        val extra = Excecao(segunda, TipoExcecao.TRABALHO, Turno(hora(8), hora(14)))
        val p = tia.copy(feriasInicio = segunda, feriasFim = segunda + 9, excecoes = mapOf(segunda to extra))
        (0..9).forEach { assertEquals(StatusDia.FERIAS, p.status(segunda + it)) }
        assertTrue(p.livre(segunda, Janela(hora(10), 60)))
        assertNull(p.excecao(segunda))
        assertEquals(StatusDia.TRABALHO, p.status(segunda + 10))
    }

    @Test
    fun plantaoNoturnoAntesDasFeriasAindaOcupaAMadrugada() {
        val p = padrasto.copy(feriasInicio = segunda + 1, feriasFim = segunda + 10)
        assertEquals(StatusDia.FERIAS, p.status(segunda + 1))
        assertFalse(p.livre(segunda + 1, Janela(hora(5), 30)))
    }

    // ---------- Busca de horários e textos ----------

    @Test
    fun encontrosSugeridosTemTodosLivres() {
        val grupo = listOf(tia, padrasto, pessoa(TipoEscala.SEMANAL, id = 3))
        val encontros = proximosEncontros(grupo, duracao = 180)
        assertTrue(encontros.isNotEmpty())
        encontros.forEach { e ->
            assertTrue(e.dia >= hoje())
            assertTrue(e.janela.duracao >= 180)
            assertTrue(todosLivres(grupo, e.dia, e.janela))
        }
        proximosEncontros(grupo, 120, PeriodoDia.NOITE, soFimDeSemana = true).forEach { e ->
            assertTrue(diaDaSemana(e.dia) == 0 || diaDaSemana(e.dia) == 6)
            assertTrue(e.janela.inicio >= hora(18))
        }
    }

    @Test
    fun padraoEmTextoIdaEVolta() {
        val padrao = listOf(Turno(hora(19), hora(7)), null, Turno(hora(7), hora(7)))
        assertEquals(padrao, textoParaPadrao(padraoParaTexto(padrao)))
        assertNull(textoParaPadrao(""))
        assertNull(textoParaPadrao("abc"))
    }

    @Test
    fun formatoAntigoViraCiclo() {
        val p = padraoLegado(TipoEscala.SEMANAL, diasSemanaDe5x2(segunda), Turno(hora(8), hora(17)))
        assertEquals(5, p.count { it != null })
        assertNull(p[0])
        assertNull(p[6])
        assertEquals(2, padraoLegado(TipoEscala.ESCALA_12X36, 0, Turno(hora(7), hora(19))).size)
    }

    @Test
    fun textosDeDiasEHorarios() {
        assertEquals("seg a sex", resumoDiasSemana(SEG_A_SEX))
        assertEquals("seg, qua e sex", resumoDiasSemana(0b0101010))
        assertEquals("todos os dias", resumoDiasSemana(0b1111111))
        assertEquals("das 18h à meia-noite", faixaHoras(hora(18), MINUTOS_DIA))
        assertEquals("6h30", horaCurta(390))
        assertEquals(1, diaDaSemana(segunda))
    }
}
