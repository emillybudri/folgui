package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes das regras dos widgets: o que aparece, quando redesenhar e para onde o toque leva
class WidgetTest {

    private val segunda = hoje().let { it + Math.floorMod(1 - diaDaSemana(it), 7) }
    private val base = segunda * MINUTOS_DIA
    private val turno = Turno(8 * 60, 17 * 60)
    private val voce = Pessoa(
        Pessoa.ID_VOCE, "Eu", TipoEscala.SEMANAL, segunda,
        padrao = TipoEscala.SEMANAL.padraoCom(turno),
        sono = Turno(23 * 60, 7 * 60)
    )

    private fun evento(titulo: String, horario: String, id: Int = 1, dia: Int = segunda, duracao: Int = 60) =
        Evento(id = id, titulo = titulo, dia = dia, horario = horario, local = "", antecedenciaMin = -1, duracaoMin = duracao)

    private fun compromisso(titulo: String, inicio: Long, fim: Long, id: Long, diaInteiro: Boolean = false) =
        CompromissoAgenda(titulo, inicio, fim, diaInteiro, "Agenda", eventoId = id)

    private fun itens(
        eventos: List<Evento> = emptyList(),
        agenda: Map<Int, List<CompromissoAgenda>> = emptyMap(),
        grupos: List<Grupo> = emptyList(),
        agora: Long = momentoEm(segunda, "09:00")
    ) = itensDoWidget(eventos, grupos, agenda, agora, segunda)

    @Test
    fun `redesenha quando o turno comeca e quando termina`() {
        assertEquals(base + 8 * 60, voce.proximaMudanca(base + 7 * 60 + 30))
        assertEquals(base + 17 * 60, voce.proximaMudanca(base + 8 * 60))
    }

    @Test
    fun `sem nada pela frente espera a virada do dia`() {
        val semSono = voce.copy(sono = null)
        assertEquals(base + MINUTOS_DIA, semSono.proximaMudanca(base + 20 * 60))
    }

    @Test
    fun `plantao que atravessa a noite avisa o fim no dia seguinte`() {
        val noturno = Pessoa(
            Pessoa.ID_VOCE, "Eu", TipoEscala.SEMANAL, segunda,
            padrao = TipoEscala.SEMANAL.padraoCom(Turno(19 * 60, 7 * 60))
        )
        val terca = base + MINUTOS_DIA
        assertEquals(terca + 7 * 60, noturno.proximaMudanca(terca + 60))
    }

    @Test
    fun `nas ferias so a virada do dia importa`() {
        val deFerias = voce.copy(feriasInicio = segunda, feriasFim = segunda + 10)
        assertEquals(base + MINUTOS_DIA, deFerias.proximaMudanca(base + 7 * 60))
    }

    @Test
    fun `a proxima mudanca sempre fica no futuro`() {
        (0 until MINUTOS_DIA step 15).forEach { minuto ->
            assertTrue(voce.proximaMudanca(base + minuto) > base + minuto)
        }
    }

    @Test
    fun `o fim de um compromisso antecipa o proximo desenho`() {
        val lista = itens(listOf(evento("Dentista", "09:00", duracao = 30)))
        assertEquals(momentoEm(segunda, "09:30"), proximoDesenho(voce, lista, base + 9 * 60))
    }

    @Test
    fun `compromisso em andamento fica e o terminado sai`() {
        val lista = itens(listOf(evento("Acabou", "07:00", 1), evento("Agora", "08:30", 2), evento("Depois", "15:00", 3)))
        assertEquals(listOf("Agora", "Depois"), lista.map { it.titulo })
    }

    @Test
    fun `evento apagado na agenda nao aparece`() {
        assertTrue(itens(listOf(evento("Apagado", "15:00").copy(foraDaAgenda = true))).isEmpty())
    }

    @Test
    fun `junta folgui e agenda em ordem de horario`() {
        val agenda = mapOf(
            segunda to listOf(compromisso("Da agenda", momentoEm(segunda, "12:00"), momentoEm(segunda, "13:00"), 9))
        )
        val lista = itens(listOf(evento("Depois", "15:00", 1), evento("Antes", "10:00", 2)), agenda)
        assertEquals(listOf("Antes", "Da agenda", "Depois"), lista.map { it.titulo })
    }

    @Test
    fun `evento de varios dias aparece uma vez so`() {
        val viagem = compromisso("Viagem", momentoEm(segunda, "10:00"), momentoEm(segunda + 2, "10:00"), 7)
        val lista = itens(agenda = (segunda..segunda + 2).associateWith { listOf(viagem) })
        assertEquals(1, lista.size)
        assertEquals(segunda, lista.single().dia)
    }

    @Test
    fun `dia inteiro vale ate a meia noite`() {
        val feriado = compromisso("Ponto facultativo", 0, 0, 5, diaInteiro = true)
        val lista = itens(agenda = mapOf(segunda to listOf(feriado)))
        assertEquals("Hoje · Dia inteiro", lista.single().quando(segunda))
        assertEquals(momentoEm(segunda + 1, "00:00"), lista.single().fim)
    }

    @Test
    fun `evento sem fim segura uma hora`() {
        val lista = itens(listOf(evento("Sem fim", "08:30", duracao = SEM_FIM)))
        assertEquals(momentoEm(segunda, "09:30"), lista.single().fim)
    }

    @Test
    fun `o ponto usa a cor do grupo`() {
        val lista = itens(listOf(evento("Consulta", "15:00").copy(grupoId = 4)), grupos = listOf(Grupo(4, "Consultas", CorGrupo.TOMATE)))
        assertEquals(CorGrupo.TOMATE, lista.single().cor)
    }

    @Test
    fun `diz quando em palavras curtas`() {
        val item = ItemWidget("Dentista", segunda + 1, "14:30", 0, 0)
        assertEquals("Hoje · 14:30", item.copy(dia = segunda).quando(segunda))
        assertEquals("Amanhã · 14:30", item.quando(segunda))
        assertEquals("${diaResumido(segunda + 5).capitalizar()} · 14:30", item.copy(dia = segunda + 5).quando(segunda))
    }

    @Test
    fun `evento do folgui abre o evento e o da agenda abre o dia`() {
        assertEquals(Rotas.evento(7), rotaDoLink(ItemWidget("A", segunda, null, 0, 0, eventoId = 7).link))
        assertEquals(Rotas.dia(segunda), rotaDoLink(ItemWidget("B", segunda, null, 0, 0).link))
    }

    @Test
    fun `links conhecidos levam a tela certa`() {
        assertEquals(Rotas.CALENDARIO, rotaDoLink(LINK_AGENDA))
        assertEquals(Rotas.EVENTOS, rotaDoLink(LINK_EVENTOS))
        assertEquals(Rotas.novoEvento(), rotaDoLink(LINK_NOVO_EVENTO))
    }

    @Test
    fun `link estranho nao abre tela nenhuma`() {
        listOf(
            null, "", LINK_APP, "https://exemplo.com", "folgui://abrir/", "folgui://abrir/dia",
            "folgui://abrir/dia/abc", "folgui://abrir/evento/0", "folgui://abrir/perfil", "folgui://outro/dia/1"
        ).forEach { assertNull(it, rotaDoLink(it)) }
    }

    @Test
    fun `a lista cresce com a altura e tem limite`() {
        assertEquals(1, linhasNaLista(80))
        assertEquals(2, linhasNaLista(170))
        assertEquals(5, linhasNaLista(900))
    }
}
