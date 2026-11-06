package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes do segundo emprego e da faculdade somados à escala principal
class AtividadeTest {

    private val domingo = diaDe(2026, 11, 1)
    private val segunda = domingo + 1
    private val sabado = domingo + 6

    private val comercial = Turno(480, 1020)
    private val aula = Turno(1140, 1320)

    private val faculdade = Atividade(
        tipo = TipoAtividade.FACULDADE,
        nome = "Unicamp",
        escala = TipoEscala.SEMANAL,
        inicioCiclo = domingo,
        padrao = listOf(null, aula, aula, aula, aula, aula, aula)
    )

    // Plantão 12x36 em outro lugar, começando na segunda
    private val plantao = Atividade(
        tipo = TipoAtividade.EMPREGO,
        nome = "Hospital",
        escala = TipoEscala.ESCALA_12X36,
        inicioCiclo = segunda,
        padrao = listOf(Turno(420, 1140), null)
    )

    private val voce = Pessoa(
        id = Pessoa.ID_VOCE,
        nome = "Eu",
        escala = TipoEscala.SEMANAL,
        inicioCiclo = domingo,
        padrao = listOf(null, comercial, comercial, comercial, comercial, comercial, null),
        atividades = listOf(faculdade)
    )

    @Test
    fun `sabado so com faculdade nao e folga`() {
        assertEquals(StatusDia.TRABALHO, voce.status(sabado))
        assertEquals("Unicamp 19h às 22h", voce.descricaoDia(sabado))
    }

    @Test
    fun `dia com emprego e faculdade soma os dois horarios`() {
        assertEquals(listOf(comercial, aula), voce.turnosNoDia(segunda))
        assertEquals(listOf(480 to 1020, 1140 to 1320), voce.periodosNoDia(segunda))
    }

    @Test
    fun `horario da faculdade nao fica livre`() {
        assertFalse(voce.livre(segunda, Janela(1200, 60)))
        assertTrue(voce.livre(segunda, Janela(1050, 60)))
    }

    @Test
    fun `domingo sem nada continua folga`() {
        assertEquals(StatusDia.FOLGA, voce.status(domingo))
    }

    @Test
    fun `segundo emprego em plantao segue o proprio ciclo`() {
        val duasEscalas = voce.copy(atividades = listOf(plantao))
        assertEquals(listOf(comercial, Turno(420, 1140)), duasEscalas.turnosNoDia(segunda))
        assertEquals(listOf(comercial), duasEscalas.turnosNoDia(segunda + 1))
    }

    @Test
    fun `faculdade tira o dia das melhores opcoes do encontro`() {
        val amigo = Pessoa(1, "Amigo", TipoEscala.SEMANAL, domingo, List(7) { null })
        assertEquals(QualidadeDia.PARTE, qualidadeDia(listOf(voce, amigo), sabado, diaHoje = domingo))
        assertEquals(QualidadeDia.MELHOR, qualidadeDia(listOf(voce, amigo), domingo + 7, diaHoje = domingo))
    }

    @Test
    fun `ferias liberam todas as atividades`() {
        val ferias = voce.copy(feriasInicio = segunda, feriasFim = sabado)
        assertTrue(ferias.turnosNoDia(segunda).isEmpty())
        assertEquals(StatusDia.FERIAS, ferias.status(sabado))
    }

    @Test
    fun `nao trabalhar no feriado libera tambem a faculdade`() {
        val respondeu = voce.copy(excecoes = mapOf(segunda to voce.respostaFeriado(segunda, trabalha = false)))
        assertEquals(StatusDia.FOLGA, respondeu.status(segunda))
    }

    @Test
    fun `atividades voltam iguais na conta`() {
        val lista = listOf(faculdade, plantao)
        assertEquals(lista, textoParaAtividades(atividadesParaTexto(lista)))
    }

    @Test
    fun `sem atividades o texto fica vazio`() {
        assertEquals("", atividadesParaTexto(emptyList()))
        assertTrue(textoParaAtividades("").isEmpty())
        assertTrue(textoParaAtividades("lixo").isEmpty())
    }

    @Test
    fun `copia da conta leva as atividades da pessoa e dos amigos`() {
        val perfil = Perfil("Eu", TipoEscala.SEMANAL, domingo, null, null, "", voce.padrao, 0, null, listOf(faculdade))
        val amigo = Pessoa(1, "Amigo", TipoEscala.SEMANAL, domingo, voce.padrao, atividades = listOf(plantao))
        val dados = DadosNuvem(perfil, emptyMap(), listOf(amigo), emptyList())
        assertEquals(dados, textoParaDados(dadosParaTexto(dados)))
    }
}
