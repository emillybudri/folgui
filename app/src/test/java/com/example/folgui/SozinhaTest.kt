package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes do uso individual: sono, dia de trabalho x folga, sequências de dias e remarcação
class SozinhaTest {

    private val segunda = diaDe(2026, 11, 2)
    private val sabado = diaDe(2026, 11, 7)
    private fun hora(h: Int) = h * 60

    // Segunda a sexta das 8h às 17h, dormindo das 23h às 7h
    private val voce = Pessoa(
        id = Pessoa.ID_VOCE,
        nome = "Eu",
        escala = TipoEscala.SEMANAL,
        inicioCiclo = segunda,
        padrao = TipoEscala.SEMANAL.padraoCom(Turno(hora(8), hora(17))),
        sono = Turno(hora(23), hora(7))
    )

    @Test
    fun sonoOcupaAsDuasPontasDaMadrugada() {
        assertEquals(listOf(0 to hora(7), hora(23) to MINUTOS_DIA), voce.sonoNoDia(sabado))
        assertFalse(voce.livre(sabado, Janela(hora(3), 60)))
        assertFalse(voce.livre(sabado, Janela(hora(22), 120)))
        assertTrue(voce.livre(sabado, Janela(hora(10), 120)))
    }

    @Test
    fun semHorarioDeSonoAMadrugadaFicaLivre() {
        assertTrue(voce.copy(sono = null).livre(sabado, Janela(hora(3), 60)))
    }

    @Test
    fun sonoNaoTransformaFolgaEmTrabalho() {
        assertEquals(StatusDia.FOLGA, voce.status(sabado))
        assertEquals("Folga o dia todo", voce.descricaoDia(sabado))
    }

    @Test
    fun trabalhoPesaMaisQueSonoNoMotivo() {
        val cedo = voce.copy(padrao = TipoEscala.SEMANAL.padraoCom(Turno(hora(6), hora(14))))
        assertEquals(TipoBloco.TRABALHO, cedo.motivoOcupado(segunda, Janela(hora(6), 120)))
        assertEquals(TipoBloco.SONO, voce.motivoOcupado(sabado, Janela(hora(2), 60)))
        assertNull(voce.motivoOcupado(sabado, Janela(hora(12), 60)))
    }

    @Test
    fun horariosLivresNaoEntramNoSono() {
        val janelas = janelasLivres(listOf(voce), sabado, de = 0)
        assertEquals(listOf(Janela(hora(7), hora(16))), janelas)
    }

    /** O problema relatado: às 20h, todos os dias apareciam iguais, como disponíveis. */
    @Test
    fun eventoANoiteDiferenciaFolgaDeDiaDeTrabalho() {
        val noite = Janela(hora(20), 120)
        assertEquals(DisponibilidadeSolo.TRABALHA_LIVRE, voce.disponibilidade(segunda, noite))
        assertEquals(DisponibilidadeSolo.FOLGA, voce.disponibilidade(sabado, noite))
    }

    @Test
    fun horarioDeTrabalhoOuSonoApareceComoOcupado() {
        assertEquals(DisponibilidadeSolo.OCUPADO, voce.disponibilidade(segunda, Janela(hora(10), 60)))
        assertEquals(DisponibilidadeSolo.OCUPADO, voce.disponibilidade(sabado, Janela(hora(23), 60)))
    }

    @Test
    fun feriasContamComoFerias() {
        val deFerias = voce.copy(feriasInicio = segunda, feriasFim = segunda + 4)
        assertEquals(DisponibilidadeSolo.FERIAS, deFerias.disponibilidade(segunda, Janela(hora(10), 60)))
    }

    @Test
    fun fraseSozinhaDizOMotivo() {
        assertEquals("Você estará dormindo nesse horário", resumoDisponibilidade(listOf(voce), sabado, Janela(hora(2), 60)).second)
        assertEquals("Você estará trabalhando nesse horário", resumoDisponibilidade(listOf(voce), segunda, Janela(hora(9), 60)).second)
        assertEquals("Dia de folga", motivoLivres(listOf(voce), sabado))
    }

    @Test
    fun turnoCompactoCabeNaCelula() {
        assertEquals("8–17h", Turno(hora(8), hora(17)).compacto)
        assertEquals("18:30–6h", Turno(hora(18) + 30, hora(6)).compacto)
        assertEquals("24h", Turno(hora(7), hora(7)).compacto)
    }

    private fun evento(dia: Int, horario: String, id: Int = 1, duracao: Int = 60) =
        Evento(id = id, titulo = "E$id", dia = dia, horario = horario, local = "", antecedenciaMin = -1, duracaoMin = duracao)

    @Test
    fun remarcarMantemOHorarioQuandoDa() {
        val opcoes = sugestoesRemarcar(evento(sabado, "20:00"), listOf(voce), emptyList(), diaHoje = segunda, minutoHoje = hora(9))
        assertEquals(segunda, opcoes[0].dia)
        assertEquals("20:00", opcoes[0].horario)
        assertEquals("Fora do horário de trabalho", opcoes[0].motivo)
    }

    @Test
    fun remarcarFogeDoTrabalhoEDoSono() {
        val e = evento(sabado, "10:00", duracao = 120)
        val opcoes = sugestoesRemarcar(e, listOf(voce), emptyList(), quantidade = 10, diaHoje = segunda, minutoHoje = hora(9))
        assertTrue(opcoes.isNotEmpty())
        opcoes.forEach { o ->
            assertTrue(o.toString(), voce.livre(o.dia, Janela(minutosDoHorario(o.horario), 120)))
        }
        // Segunda não tem 10h livre: usa o horário depois do expediente
        assertEquals("17:00", opcoes.first { it.dia == segunda }.horario)
    }

    @Test
    fun remarcarNaoRepeteOHorarioAtualNemCaiEmOutroEvento() {
        val e = evento(sabado, "10:00", id = 1)
        val outro = evento(sabado + 1, "10:00", id = 2)
        val opcoes = sugestoesRemarcar(e, listOf(voce), listOf(e, outro), quantidade = 7, diaHoje = sabado, minutoHoje = hora(8))
        assertFalse(opcoes.any { it.dia == sabado && it.horario == "10:00" })
        assertFalse(opcoes.any { it.dia == sabado + 1 && it.horario == "10:00" })
    }

    @Test
    fun remarcarHojeSoDepoisDeAgora() {
        val opcoes = sugestoesRemarcar(evento(sabado + 7, "10:00"), listOf(voce), emptyList(), diaHoje = sabado, minutoHoje = hora(15))
        assertTrue(minutosDoHorario(opcoes.first { it.dia == sabado }.horario) > hora(15))
    }

    @Test
    fun eventoSoDaPessoaNaoViraEventoDeTodos() {
        assertEquals(emptySet<Int>(), textoParaParticipantes(participantesParaTexto(emptySet())))
        assertNull(textoParaParticipantes("*"))
    }
}
