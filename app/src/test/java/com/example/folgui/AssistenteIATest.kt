package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes do pedido enviado ao Gemini, da leitura da resposta e da conferência das ações
class AssistenteIATest {

    private val amanha = hoje() + 1
    private val iso = diaParaIso(amanha + 30)
    private fun hora(h: Int) = h * 60

    private val voce = Pessoa(
        id = Pessoa.ID_VOCE,
        nome = "Eu",
        escala = TipoEscala.ESCALA_12X36,
        inicioCiclo = amanha,
        padrao = TipoEscala.ESCALA_12X36.padraoCom(Turno(hora(8), hora(18))),
        sono = Turno(hora(23), hora(7))
    )
    private val evento = Evento(id = 7, titulo = "Dentista", dia = amanha + 1, horario = "15:00", local = "", antecedenciaMin = 60, duracaoMin = 60, participantes = emptySet())
    private val todas = listOf(voce)

    @Test
    fun leRespostaDoGeminiIgnorandoRaciocinio() {
        val corpo = """{"candidates":[{"content":{"parts":[{"text":"pensando","thought":true},{"text":"{\"a\":1}"}]}}]}"""
        assertEquals("""{"a":1}""", extrairTexto(corpo))
    }

    @Test(expected = ErroIA::class)
    fun respostaBloqueadaViraErroLegivel() {
        extrairTexto("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
    }

    @Test
    fun leEntendiTranscricaoEAcoes() {
        val r = interpretarResposta(
            """{"transcricao":"marca dentista","entendi":"Marcar dentista","resposta":"Confira","acoes":[
               {"tipo":"criar_evento","titulo":"Dentista","dia":"$iso","horario":"15:00","duracao_min":60}]}"""
        )
        assertEquals("marca dentista", r.transcricao)
        assertEquals("Marcar dentista", r.mensagem.entendi)
        assertEquals(TipoAcaoIA.CRIAR_EVENTO, r.mensagem.acoes.single().tipo)
        assertEquals("15:00", r.mensagem.acoes.single().horario)
    }

    @Test
    fun textoSoltoViraRespostaSemAcoes() {
        val r = interpretarResposta("Não entendi, pode repetir?")
        assertEquals("Não entendi, pode repetir?", r.mensagem.texto)
        assertTrue(r.mensagem.acoes.isEmpty())
    }

    @Test
    fun acaoIncompletaOuDesconhecidaEDescartada() {
        val r = interpretarResposta(
            """{"resposta":"x","acoes":[{"tipo":"criar_evento","titulo":"Sem data"},
               {"tipo":"apagar_tudo"},{"tipo":"editar_evento","evento_id":7},{"tipo":"sono","entrada":"99:00","saida":"07:00"}]}"""
        )
        assertTrue(r.mensagem.acoes.isEmpty())
    }

    /** Caso real: a IA disse "agendei" e mandou o evento sem horário. */
    @Test
    fun acaoIncompletaNaoPedeConfirmacaoDoQueNaoExiste() {
        val r = interpretarResposta(
            """{"entendi":"x","resposta":"Agendei! Pode confirmar?","acoes":[{"tipo":"criar_evento","titulo":"Academia","dia":"$iso"}]}"""
        )
        assertTrue(r.mensagem.acoes.isEmpty())
        assertFalse(r.mensagem.texto, "Agendei" in r.mensagem.texto)
    }

    @Test
    fun parteIncompletaGeraAviso() {
        val r = interpretarResposta(
            """{"resposta":"Confira","acoes":[{"tipo":"criar_evento","titulo":"A"},{"tipo":"excluir_evento","evento_id":7}]}"""
        )
        assertEquals(1, r.mensagem.acoes.size)
        assertEquals(1, r.mensagem.avisos.size)
    }

    @Test
    fun aPropriaPessoaNaoEntraNosParticipantes() {
        val r = interpretarResposta(
            """{"resposta":"x","acoes":[{"tipo":"criar_evento","titulo":"A","dia":"$iso","horario":"10:00","participantes":[-1]}]}"""
        )
        assertEquals(emptySet<Int>(), r.mensagem.acoes.single().participantes)
    }

    @Test
    fun eventoIdZeroSignificaSemEvento() {
        val r = interpretarResposta(
            """{"resposta":"x","acoes":[{"tipo":"criar_evento","evento_id":0,"titulo":"A","dia":"$iso","horario":"10:00"}]}"""
        )
        assertEquals(null, r.mensagem.acoes.single().eventoId)
    }

    @Test
    fun campoVazioNaEdicaoNaoApagaDado() {
        val comLocal = evento.copy(local = "Centro")
        val r = interpretarResposta(
            """{"resposta":"x","acoes":[{"tipo":"editar_evento","evento_id":7,"titulo":"","dia":"","horario":"16:00","local":""}]}"""
        )
        val depois = eventoResultante(r.mensagem.acoes.single(), listOf(comLocal))!!
        assertEquals("Centro", depois.local)
        assertEquals("Dentista", depois.titulo)
        assertEquals("16:00", depois.horario)
    }

    @Test
    fun edicaoSoTrocaOQueFoiPedido() {
        val a = AcaoIA(TipoAcaoIA.EDITAR_EVENTO, eventoId = 7, horario = "16:30")
        val depois = eventoResultante(a, listOf(evento))!!
        assertEquals("16:30", depois.horario)
        assertEquals(evento.dia, depois.dia)
        assertEquals("Dentista", depois.titulo)
        assertEquals(7, depois.id)
    }

    @Test
    fun eventoCriadoPelaIAESoDeQuemPediu() {
        val a = AcaoIA(TipoAcaoIA.CRIAR_EVENTO, titulo = "X", dia = amanha, horario = "20:00")
        assertEquals(emptySet<Int>(), eventoResultante(a, emptyList())!!.participantes)
    }

    @Test
    fun conferenciaAvisaSonoTrabalhoEConflito() {
        fun avisos(dia: Int, horario: String) =
            verificarAcoes(
                listOf(AcaoIA(TipoAcaoIA.CRIAR_EVENTO, titulo = "Novo", dia = dia, horario = horario)),
                todas,
                listOf(evento)
            ).second

        assertTrue(avisos(amanha + 1, "03:00").any { "dormindo" in it })
        assertTrue(avisos(amanha, "10:00").any { "trabalhando" in it })
        assertTrue(avisos(amanha + 1, "15:30").any { "Dentista" in it })
        assertEquals(emptyList<String>(), avisos(amanha + 1, "11:00"))
    }

    @Test
    fun conferenciaTiraEventoOuPessoaInexistente() {
        val (validas, avisos) = verificarAcoes(
            listOf(
                AcaoIA(TipoAcaoIA.EXCLUIR_EVENTO, eventoId = 999),
                AcaoIA(TipoAcaoIA.FOLGA, pessoaId = 42, dia = amanha),
                AcaoIA(TipoAcaoIA.EXCLUIR_EVENTO, eventoId = 7)
            ),
            todas,
            listOf(evento)
        )
        assertEquals(1, validas.size)
        assertEquals(2, avisos.size)
    }

    @Test
    fun conferenciaAvisaFolgaEmDiaJaLivreEFeriasSubstituidas() {
        val comFerias = voce.copy(feriasInicio = amanha + 60, feriasFim = amanha + 70)
        val (_, avisos) = verificarAcoes(
            listOf(
                AcaoIA(TipoAcaoIA.FOLGA, dia = amanha + 1),
                AcaoIA(TipoAcaoIA.FERIAS, dia = amanha + 90, fimDia = amanha + 95)
            ),
            listOf(comFerias),
            emptyList()
        )
        assertTrue(avisos.any { "dia livre" in it })
        assertTrue(avisos.any { "substitui" in it })
    }

    @Test
    fun remarcacaoMostraOAntesEODepois() {
        val texto = descreverAcao(AcaoIA(TipoAcaoIA.EDITAR_EVENTO, eventoId = 7, horario = "16:00"), todas, listOf(evento))
        assertTrue(texto, "15:00" in texto && "16:00" in texto && "→" in texto)
    }

    @Test
    fun pedidoLevaAudioEEsquema() {
        val corpo = montarCorpo("ctx", emptyList(), todas, emptyList(), null, "QUJD")
        val partes = corpo.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        val audio = partes.getJSONObject(1).getJSONObject("inlineData")
        assertEquals(MIME_AUDIO, audio.getString("mimeType"))
        assertEquals("QUJD", audio.getString("data"))
        assertTrue(corpo.getJSONObject("generationConfig").has("responseJsonSchema"))
    }

    @Test
    fun semEsquemaContinuaPedindoJson() {
        val config = montarCorpo("ctx", emptyList(), todas, emptyList(), "oi", null, estruturado = false)
            .getJSONObject("generationConfig")
        assertEquals("application/json", config.getString("responseMimeType"))
        assertFalse(config.has("responseJsonSchema"))
    }

    @Test
    fun historicoComecaNaPessoaELevaAPropostaRecusada() {
        val historico = listOf(
            MensagemIA(deUsuario = false, texto = "Mensagem solta"),
            MensagemIA(deUsuario = true, texto = "Marca às 15h"),
            MensagemIA(
                deUsuario = false,
                texto = "Confira",
                acoes = listOf(AcaoIA(TipoAcaoIA.EDITAR_EVENTO, eventoId = 7, horario = "15:30")),
                situacao = SituacaoAcoes.DESCARTADAS
            )
        )
        val conteudos = montarCorpo("ctx", historico, todas, listOf(evento), "não, às 16h", null).getJSONArray("contents")
        assertEquals(3, conteudos.length())
        assertEquals("user", conteudos.getJSONObject(0).getString("role"))
        val anterior = conteudos.getJSONObject(1).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(anterior, "recusadas" in anterior && "15:30" in anterior)
    }

    @Test
    fun contextoTrazSonoEEventosComId() {
        val ctx = contextoParaIA(todas, listOf(evento), emptyMap(), emptyMap())
        assertTrue("dorme 23h às 7h" in ctx)
        assertTrue("evento_id 7" in ctx)
    }
}
