package com.example.folgui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Testes dos amigos conectados: código, privacidade da escala e sincronização
class AmigosTest {

    private val dia = hoje()
    private val voce = Pessoa(
        id = Pessoa.ID_VOCE,
        nome = "Ana",
        escala = TipoEscala.ESCALA_12X36,
        inicioCiclo = dia,
        excecoes = mapOf(dia + 2 to Excecao(dia + 2, TipoExcecao.AUSENCIA, null)),
        sono = Turno(22 * 60, 6 * 60),
        atividades = listOf(
            Atividade(TipoAtividade.entries.first(), "Hospital São Lucas", TipoEscala.SEMANAL, dia, List(7) { null })
        )
    )

    @Test
    fun `codigo tem 8 letras que nao se confundem`() {
        val codigo = gerarCodigoAmigo(Random(1))
        assertTrue(codigoValido(codigo))
        assertTrue(codigo.none { it in "01OIL" })
    }

    @Test
    fun `aceita codigo colado com traco e minuscula`() {
        assertEquals("ABCD2345", limparCodigo(" abcd-2345 "))
        assertEquals("ABCD-2345", codigoParaMostrar("ABCD2345"))
        assertFalse(codigoValido(limparCodigo("abc")))
    }

    @Test
    fun `a escala volta igual do outro lado`() {
        val amigo = escalaDoAmigo(escalaParaAmigo(voce), "Ana", "uid1")!!
        assertEquals(voce.escala, amigo.escala)
        assertEquals(voce.inicioCiclo, amigo.inicioCiclo)
        assertEquals(voce.padrao, amigo.padrao)
        assertEquals("uid1", amigo.amigoUid)
    }

    @Test
    fun `amigo nao ve atestado sono nem o nome do emprego`() {
        val texto = escalaParaAmigo(voce)
        assertFalse(texto.contains("Hospital"))
        assertFalse(JSONObject(texto).has("sono"))
        assertEquals(TipoExcecao.FOLGA, escalaDoAmigo(texto, "Ana", "u")!!.excecoes[dia + 2]?.tipo)
    }

    @Test
    fun `texto corrompido nao vira amigo`() {
        assertNull(escalaDoAmigo("isso não é json", "Ana", "u"))
    }

    private val manual = Pessoa(1, "Tia", TipoEscala.SEMANAL, dia)
    private val bia = Pessoa(0, "Bia", TipoEscala.SEMANAL, dia, amigoUid = "b")

    @Test
    fun `amigo novo entra e pessoa manual fica`() {
        val m = mudancasAmigos(listOf(manual), setOf("b"), listOf(bia))
        assertEquals(listOf(bia), m.novos)
        assertTrue(m.removidos.isEmpty())
    }

    @Test
    fun `amigo que mudou a escala atualiza mantendo o id`() {
        val local = bia.copy(id = 5)
        val remoto = bia.copy(escala = TipoEscala.ESCALA_2X2)
        val m = mudancasAmigos(listOf(local), setOf("b"), listOf(remoto))
        assertEquals(listOf(remoto.copy(id = 5)), m.alterados)
    }

    @Test
    fun `amigo igual nao gera escrita`() {
        val local = bia.copy(id = 5)
        val m = mudancasAmigos(listOf(local), setOf("b"), listOf(bia))
        assertTrue(m.novos.isEmpty() && m.alterados.isEmpty() && m.removidos.isEmpty())
    }

    @Test
    fun `amizade desfeita sai da lista mas falha de leitura nao`() {
        val local = bia.copy(id = 5)
        assertEquals(listOf(local), mudancasAmigos(listOf(local, manual), emptySet(), emptyList()).removidos)
        assertTrue(mudancasAmigos(listOf(local), setOf("b"), emptyList()).removidos.isEmpty())
    }

    @Test
    fun `convite vai so para amigos escolhidos`() {
        val amiga = bia.copy(id = 5)
        val evento = Evento(titulo = "Almoço", dia = dia, horario = "12:00", local = "", antecedenciaMin = 60, participantes = setOf(5, 1))
        assertEquals(listOf(amiga), amigosDoEvento(evento, listOf(amiga, manual)))
        assertTrue(amigosDoEvento(evento.copy(participantes = null), listOf(amiga)).isEmpty())
    }

    @Test
    fun `convite aceito vira evento com quem convidou`() {
        val convite = Convite("c", "b", "Bia", "Almoço", dia, "12:00", SEM_FIM, "Centro")
        val evento = eventoDoConvite(convite, listOf(bia.copy(id = 5)))
        assertEquals(setOf(5), evento.participantes)
        assertTrue(evento.semFim)
    }
}
