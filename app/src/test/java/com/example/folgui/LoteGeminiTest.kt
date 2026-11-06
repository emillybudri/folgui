package com.example.folgui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Conversa de verdade com o Gemini, com a lista do pedido original. Gasta cota, então só roda com
 * `-Dfolgui.ia=1` (ou FOLGUI_IA=1); nos testes comuns, é ignorado.
 */
class LoteGeminiTest {

    private val lista = """
        IA, adicione todas estas consultas ao meu Google Calendar:
        - 24/11/2026 às 16:12 – Dra. MAYARA GOMES DE SOUZA (psicóloga)
        - 28/11/2026 às 12:00 – Dra. ALEÍRA (dentista)
        - 01/12/2026 às 16:12 – Dra. MAYARA GOMES DE SOUZA (psicóloga)
        - 01/12/2026 às 16:40 – Dra. BRUNA DE ALMEIDA DELGADO RIBEIRO (nutricionista)
        - 07/12/2026 às 09:45 – Dra. CRIATIANE BIVAR CAVALCANTI DE OLIVEIRA (psiquiatra)
        - 08/12/2026 às 16:12 – Dra. MAYARA GOMES DE SOUZA (psicóloga)
        - 09/12/2026 às 13:48 – Dra. DANIELA GADELHA DE BRITO (psicóloga — emagrecimento)
        - 05/01/2027 às 08:00 – ALINY AMIGO NUNES (educadora física)
    """.trimIndent()

    private val grupos = listOf(Grupo(1, "Consultas", CorGrupo.TOMATE))
    private val eu = Pessoa(id = Pessoa.ID_VOCE, nome = "Eu", escala = TipoEscala.SEMANAL, inicioCiclo = hoje(), padrao = listOf(null))

    private fun env(): Map<String, String> = listOf(File("../.env"), File(".env")).firstOrNull { it.exists() }?.readLines().orEmpty()
        .filter { "=" in it }.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim().trim('"') }

    private val assistente by lazy {
        val e = env()
        AssistenteIA(e["GEMINI_API_KEY"].orEmpty(), e["GEMINI_MODEL"] ?: "gemini-3-flash-preview")
    }

    private val historico = mutableListOf<MensagemIA>()
    private var plano: PlanoLote? = null

    /** Uma fala da conversa, passando pelo mesmo caminho do app. */
    private fun fala(texto: String): AjusteLote? = runBlocking {
        val contexto = contextoParaIA(listOf(eu), emptyList(), emptyMap(), emptyMap(), grupos, plano)
        val r = assistente.conversar(contexto, historico.toList(), listOf(eu), emptyList(), texto)
        historico.add(MensagemIA(deUsuario = true, texto = texto))
        val ajuste = r.ajuste
        val resposta = if (ajuste != null) {
            plano = aplicarAjuste(plano, ajuste, grupos)
            plano?.let { perguntaDoPlano(it, grupos, ajuste.novos.size) } ?: "Cancelado"
        } else {
            r.mensagem.texto
        }
        println("VOCÊ: ${texto.lines().first()}\nIA: $resposta\n  ajuste=$ajuste\n")
        historico.add(r.mensagem.copy(texto = resposta))
        ajuste
    }

    private fun exigirChave() {
        assumeTrue(System.getenv("FOLGUI_IA") == "1" || System.getProperty("folgui.ia") == "1")
        assumeTrue(env()["GEMINI_API_KEY"].orEmpty().isNotBlank())
    }

    @Test
    fun `conversa do pedido original, do inicio a confirmacao`() {
        exigirChave()

        fala(lista)
        val p1 = assertNotNullPlano()
        assertEquals(8, p1.itens.size)
        assertEquals(
            listOf(
                "2026-11-24 16:12", "2026-11-28 12:00", "2026-12-01 16:12", "2026-12-01 16:40",
                "2026-12-07 09:45", "2026-12-08 16:12", "2026-12-09 13:48", "2027-01-05 08:00"
            ),
            p1.itens.map { "${diaParaIso(it.dia)} ${it.horario}" }
        )
        assertEquals("Dra. CRIATIANE BIVAR CAVALCANTI DE OLIVEIRA (psiquiatra)", p1.itens[4].titulo)
        assertEquals("ALINY AMIGO NUNES (educadora física)", p1.itens[7].titulo)
        assertFalse("grupo só é decidido pela pessoa", p1.grupoDecidido)

        fala("Sim!")
        val p2 = assertNotNullPlano()
        assertEquals("Consultas", p2.grupoNome)
        assertEquals(CorGrupo.TOMATE, p2.cor)

        fala("Sim, me avise 1 dia antes e 1 hora antes.")
        val p3 = assertNotNullPlano()
        assertEquals(listOf(1440, 60), p3.avisos)
        assertTrue(planoPronto(p3))

        fala("muda para azul")
        assertEquals(CorGrupo.PAVAO, assertNotNullPlano().cor)
        assertEquals(8, assertNotNullPlano().itens.size)

        val confirmar = fala("pode criar")
        assertNotNull(confirmar)
        assertTrue(confirmar!!.confirmar)
    }

    @Test
    fun `respostas curtas sobre parte da lista`() {
        exigirChave()
        fala(lista)
        fala("não quero grupo")
        assertFalse(assertNotNullPlano().temGrupo)
        assertTrue(assertNotNullPlano().grupoDecidido)
        fala("me avise 1 dia antes só nos três primeiros")
        val p = assertNotNullPlano()
        assertEquals(listOf(1440), avisosDoItem(p.itens[0], p))
        assertEquals(listOf(1440), avisosDoItem(p.itens[2], p))
        assertTrue(avisosDoItem(p.itens[3], p).isEmpty())
        fala("tira a dentista")
        assertEquals(7, assertNotNullPlano().itens.size)
        assertTrue(assertNotNullPlano().itens.none { "ALEÍRA" in it.titulo })
    }

    @Test
    fun `pedido completo vai direto para a previa`() {
        exigirChave()
        fala("$lista\nColoque tudo no grupo Consultas, mantendo a cor, duração de 50 minutos e me avise 1 hora antes.")
        val p = assertNotNullPlano()
        assertEquals(8, p.itens.size)
        assertTrue(planoPronto(p))
        assertEquals(listOf(60), p.avisos)
        assertEquals(50, duracaoDoItem(p.itens[0], p))
    }

    @Test
    fun `um compromisso so continua pelo caminho de sempre`() {
        exigirChave()
        val contexto = contextoParaIA(listOf(eu), emptyList(), emptyMap(), emptyMap(), grupos)
        val r = runBlocking { assistente.conversar(contexto, emptyList(), listOf(eu), emptyList(), "Marque dentista dia 24/11/2026 às 16h") }
        val final = semLoteDeUmSo(r, false, grupos)
        assertEquals(null, final.ajuste)
        assertEquals(TipoAcaoIA.CRIAR_EVENTO, final.mensagem.acoes.single().tipo)
    }

    private fun assertNotNullPlano(): PlanoLote = plano ?: error("Sem plano")
}
