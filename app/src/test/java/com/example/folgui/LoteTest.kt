package com.example.folgui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes da lista de compromissos: leitura da resposta da IA, conversa, conferência e criação
class LoteTest {

    /** A resposta da IA para a lista de 8 consultas do pedido original. */
    private val respostaOito = """
        {"entendi":"8 consultas","resposta":"Encontrei.","acoes":[],"lote":{"grupo_sugerido":"Consultas","novos":[
        {"titulo":"Dra. MAYARA GOMES DE SOUZA (psicóloga)","profissional":"Dra. MAYARA GOMES DE SOUZA","categoria":"psicóloga","dia":"2026-11-24","ano_informado":true,"horario":"16:12"},
        {"titulo":"Dra. ALEÍRA (dentista)","profissional":"Dra. ALEÍRA","categoria":"dentista","dia":"2026-11-28","ano_informado":true,"horario":"12:00"},
        {"titulo":"Dra. MAYARA GOMES DE SOUZA (psicóloga)","profissional":"Dra. MAYARA GOMES DE SOUZA","categoria":"psicóloga","dia":"2026-12-01","ano_informado":true,"horario":"16:12"},
        {"titulo":"Dra. BRUNA DE ALMEIDA DELGADO RIBEIRO (nutricionista)","profissional":"Dra. BRUNA DE ALMEIDA DELGADO RIBEIRO","categoria":"nutricionista","dia":"2026-12-01","ano_informado":true,"horario":"16:40"},
        {"titulo":"Dra. CRIATIANE BIVAR CAVALCANTI DE OLIVEIRA (psiquiatra)","profissional":"Dra. CRIATIANE BIVAR CAVALCANTI DE OLIVEIRA","categoria":"psiquiatra","dia":"2026-12-07","ano_informado":true,"horario":"09:45"},
        {"titulo":"Dra. MAYARA GOMES DE SOUZA (psicóloga)","profissional":"Dra. MAYARA GOMES DE SOUZA","categoria":"psicóloga","dia":"2026-12-08","ano_informado":true,"horario":"16:12"},
        {"titulo":"Dra. DANIELA GADELHA DE BRITO (psicóloga — emagrecimento)","profissional":"Dra. DANIELA GADELHA DE BRITO","categoria":"psicóloga — emagrecimento","dia":"2026-12-09","ano_informado":true,"horario":"13:48"},
        {"titulo":"ALINY AMIGO NUNES (educadora física)","profissional":"ALINY AMIGO NUNES","categoria":"educadora física","dia":"2027-01-05","ano_informado":true,"horario":"08:00"}
        ]}}
    """

    private val ajusteOito = interpretarResposta(respostaOito).ajuste!!
    private val consultas = Grupo(1, "Consultas", CorGrupo.TOMATE)
    private val grupos = listOf(consultas)
    private val plano = aplicarAjuste(null, ajusteOito, grupos)!!

    private val pronto = plano.copy(grupoDecidido = true, grupoNome = "Consultas", cor = CorGrupo.TOMATE, corDecidida = true, avisos = listOf(1440, 60))

    private fun evento(id: Int, titulo: String, dia: Int, horario: String, duracao: Int = 60) =
        Evento(id = id, titulo = titulo, dia = dia, horario = horario, local = "", antecedenciaMin = -1, duracaoMin = duracao, participantes = emptySet())

    private fun item(n: Int, titulo: String, dia: Int, horario: String) = ItemLote(n = n, titulo = titulo, dia = dia, horario = horario)

    // ---------- Leitura da lista ----------

    @Test
    fun `reconhece os oito compromissos com data e hora certas`() {
        assertEquals(8, plano.itens.size)
        assertEquals(
            listOf(
                "2026-11-24 16:12", "2026-11-28 12:00", "2026-12-01 16:12", "2026-12-01 16:40",
                "2026-12-07 09:45", "2026-12-08 16:12", "2026-12-09 13:48", "2027-01-05 08:00"
            ),
            plano.itens.map { "${diaParaIso(it.dia)} ${it.horario}" }
        )
    }

    @Test
    fun `preserva os nomes, inclusive os longos, e separa profissional e categoria`() {
        val quinto = plano.itens[4]
        assertEquals("Dra. CRIATIANE BIVAR CAVALCANTI DE OLIVEIRA (psiquiatra)", quinto.titulo)
        assertEquals("Dra. CRIATIANE BIVAR CAVALCANTI DE OLIVEIRA", quinto.profissional)
        assertEquals("psiquiatra", quinto.categoria)
        assertEquals("Profissional: Dra. CRIATIANE BIVAR CAVALCANTI DE OLIVEIRA\nCategoria: psiquiatra", eventoDoItem(quinto, plano).detalhes)
    }

    @Test
    fun `lista nao passa pela confirmacao de acoes comuns`() {
        assertTrue(interpretarResposta(respostaOito).mensagem.acoes.isEmpty())
    }

    @Test
    fun `sem ano informado a data passada vai para o proximo ano, com ano escrito ela fica`() {
        val passado = diaParaIso(hoje() - 60)
        fun resposta(informado: Boolean) = """{"resposta":"","acoes":[],"lote":{"novos":[
            {"titulo":"A","dia":"$passado","ano_informado":$informado,"horario":"10:00"}]}}"""
        val semAno = interpretarResposta(resposta(false)).ajuste!!.novos[0]
        val comAno = interpretarResposta(resposta(true)).ajuste!!.novos[0]
        assertTrue(semAno.dia > hoje())
        assertEquals(isoParaDia(passado), comAno.dia)
    }

    @Test
    fun `compromisso sem hora fica de fora e e contado`() {
        val r = interpretarResposta("""{"resposta":"","acoes":[],"lote":{"novos":[
            {"titulo":"A","dia":"2027-01-05","ano_informado":true,"horario":"08:00"},
            {"titulo":"B","dia":"2027-01-06","ano_informado":true,"horario":""}]}}""")
        assertEquals(1, r.ajuste!!.novos.size)
        assertEquals(1, r.ajuste!!.incompletos)
    }

    @Test
    fun `mesma linha repetida na mensagem vira um item so`() {
        val novo = NovoItem(titulo = "Dentista", dia = hoje() + 5, horario = "10:00")
        val p = aplicarAjuste(null, AjusteLote(novos = listOf(novo, novo.copy(titulo = " DENTISTA"))), grupos)!!
        assertEquals(1, p.itens.size)
    }

    @Test
    fun `lista fora de ordem fica em ordem de data`() {
        val p = aplicarAjuste(
            null,
            AjusteLote(novos = listOf(NovoItem(titulo = "B", dia = hoje() + 9, horario = "10:00"), NovoItem(titulo = "A", dia = hoje() + 2, horario = "10:00"))),
            grupos
        )!!
        assertEquals(listOf(1 to "A", 2 to "B"), p.itens.map { it.n to it.titulo })
    }

    @Test
    fun `lista visivel na mensagem sem nenhum item volta a ser pedida`() {
        val vazia = interpretarResposta("""{"resposta":"Encontrei 8 consultas.","acoes":[],"lote":{"novos":[]}}""")
        val pedido = "24/11/2026 às 16:12 Dra. ANA\n28/11/2026 às 12:00 Dra. BIA"
        assertTrue(listaPerdida(pedido, vazia))
        assertFalse("com itens, está tudo certo", listaPerdida(pedido, interpretarResposta(respostaOito)))
        assertFalse("pergunta com duas datas não é lista", listaPerdida("O que tenho entre 10/12 e 15/12?", vazia))
        assertFalse("áudio não tem texto para conferir", listaPerdida(null, vazia))
    }

    @Test
    fun `esquema obriga o lote e seus campos, ou o modelo os omite`() {
        val corpo = org.json.JSONObject(montarCorpo("", emptyList(), emptyList(), emptyList(), "oi", null).toString())
        val esquema = corpo.getJSONObject("generationConfig").getJSONObject("responseJsonSchema")
        assertTrue("\"lote\"" in esquema.getJSONArray("required").toString())
        val obrigatorios = esquema.getJSONObject("properties").getJSONObject("lote").getJSONArray("required").toString()
        listOf("novos", "grupo", "cor", "avisos_min", "confirmar").forEach { assertTrue(it, "\"$it\"" in obrigatorios) }
    }

    @Test
    fun `um compromisso sozinho vira acao comum, sem perguntas de lista`() {
        val item = NovoItem(titulo = "Dentista", dia = hoje() + 5, horario = "16:00")
        val r = RespostaIA(MensagemIA(deUsuario = false, texto = ""), ajuste = AjusteLote(novos = listOf(item), grupo = "Consultas", avisos = listOf(60)))
        val acao = semLoteDeUmSo(r, false, grupos).also { assertNull(it.ajuste) }.mensagem.acoes.single()
        assertEquals(TipoAcaoIA.CRIAR_EVENTO, acao.tipo)
        assertEquals("16:00", acao.horario)
        assertEquals(1, acao.grupoId)
        assertEquals(listOf(60), acao.avisos)
    }

    @Test
    fun `compromisso unico fica no plano se ha plano aberto ou grupo novo`() {
        val item = NovoItem(titulo = "Dentista", dia = hoje() + 5, horario = "16:00")
        val r = RespostaIA(MensagemIA(deUsuario = false, texto = ""), ajuste = AjusteLote(novos = listOf(item)))
        assertNotNull(semLoteDeUmSo(r, true, grupos).ajuste)
        val novoGrupo = r.copy(ajuste = r.ajuste!!.copy(grupo = "Saúde"))
        assertNotNull(semLoteDeUmSo(novoGrupo, false, grupos).ajuste)
    }

    // ---------- Conversa ----------

    @Test
    fun `primeira fala pergunta do grupo sugerido`() {
        assertEquals("Encontrei 8 compromissos! Quer que eu organize todos no grupo Consultas?", perguntaDoPlano(plano, grupos, novos = 8))
    }

    @Test
    fun `sim para o grupo existente traz a cor dele e pergunta das notificacoes`() {
        val p = aplicarAjuste(plano, AjusteLote(grupo = "consultas"), grupos)!!
        assertEquals("Consultas", p.grupoNome)
        assertEquals(CorGrupo.TOMATE, p.cor)
        assertEquals(
            "Perfeito! O grupo Consultas já existe e usa a cor vermelha. Quer manter essa cor? E quer receber notificações antes dos compromissos?",
            perguntaDoPlano(p, grupos)
        )
    }

    @Test
    fun `sim com dois avisos deixa o plano pronto para confirmar`() {
        var p = aplicarAjuste(plano, AjusteLote(grupo = "Consultas"), grupos)!!
        assertFalse(planoPronto(p))
        p = aplicarAjuste(p, AjusteLote(manterCor = true, avisos = listOf(60, 1440)), grupos)!!
        assertTrue(planoPronto(p))
        assertEquals(listOf(1440, 60), p.avisos)
        assertEquals("Confira a prévia. Nada é criado até você confirmar.", perguntaDoPlano(p, grupos))
    }

    @Test
    fun `pedido completo nao faz nenhuma pergunta`() {
        val p = aplicarAjuste(null, ajusteOito.copy(grupo = "Consultas", manterCor = true, avisos = listOf(1440)), grupos)!!
        assertTrue(planoPronto(p))
        assertFalse("?" in perguntaDoPlano(p, grupos, novos = 8))
    }

    @Test
    fun `nao para o grupo deixa sem grupo e sem cor`() {
        val p = aplicarAjuste(plano, AjusteLote(semGrupo = true, semAvisos = true), grupos)!!
        assertTrue(planoPronto(p))
        assertFalse(p.temGrupo)
        assertNull(p.cor)
        assertEquals(0, eventoDoItem(p.itens[0], p).grupoId)
    }

    @Test
    fun `cor em plano sem grupo e ignorada`() {
        val p = aplicarAjuste(plano.copy(grupoDecidido = true), AjusteLote(cor = CorGrupo.PAVAO), grupos)!!
        assertNull(p.cor)
    }

    @Test
    fun `muda para azul troca a cor sem perder o resto`() {
        val p = aplicarAjuste(pronto, AjusteLote(cor = CorGrupo.PAVAO), grupos)!!
        assertEquals(CorGrupo.PAVAO, p.cor)
        assertEquals(listOf(1440, 60), p.avisos)
        assertEquals(8, p.itens.size)
    }

    @Test
    fun `grupo novo pergunta a cor`() {
        val p = aplicarAjuste(plano, AjusteLote(grupo = "Saúde"), grupos)!!
        assertEquals("Qual cor o grupo Saúde vai ter? E quer receber notificações antes dos compromissos?", perguntaDoPlano(p, grupos))
    }

    @Test
    fun `so nos tres primeiros vale so para eles`() {
        val p = aplicarAjuste(plano, AjusteLote(avisos = listOf(1440), itensAvisos = listOf(1, 2, 3)), grupos)!!
        assertEquals(listOf(1440), avisosDoItem(p.itens[2], p))
        assertTrue(avisosDoItem(p.itens[3], p).isEmpty())
        assertNotNull(p.avisos)
    }

    @Test
    fun `manter so os tres primeiros tira o resto`() {
        val p = aplicarAjuste(plano, AjusteLote(manter = listOf(1, 2, 3)), grupos)!!
        assertEquals(listOf(1, 2, 3), p.itens.map { it.n })
    }

    @Test
    fun `corrige e tira itens individualmente`() {
        val p = aplicarAjuste(plano, AjusteLote(remover = listOf(2), editar = listOf(EdicaoItem(n = 3, horario = "15:00"))), grupos)!!
        assertEquals(7, p.itens.size)
        assertEquals("15:00", p.itens.first { it.n == 3 }.horario)
        assertEquals(plano.itens[0], p.itens[0])
    }

    @Test
    fun `duracao padrao e editavel para todos ou para alguns`() {
        assertEquals(DURACAO_PADRAO_LOTE, duracaoDoItem(plano.itens[0], plano))
        val todos = aplicarAjuste(plano, AjusteLote(duracaoMin = 50), grupos)!!
        assertEquals(50, duracaoDoItem(todos.itens[7], todos))
        val alguns = aplicarAjuste(plano, AjusteLote(duracaoMin = 30, itensDuracao = listOf(4)), grupos)!!
        assertEquals(30, duracaoDoItem(alguns.itens[3], alguns))
        assertEquals(DURACAO_PADRAO_LOTE, duracaoDoItem(alguns.itens[0], alguns))
    }

    @Test
    fun `cancelar descarta o plano`() {
        assertNull(aplicarAjuste(plano, AjusteLote(cancelar = true), grupos))
    }

    @Test
    fun `tirar todos os itens encerra o plano`() {
        assertNull(aplicarAjuste(plano, AjusteLote(remover = plano.itens.map { it.n }), grupos))
    }

    @Test
    fun `confirmar sozinho nao muda o plano, confirmar junto com mudanca muda`() {
        assertFalse(AjusteLote(confirmar = true).mudaPlano)
        assertTrue(AjusteLote(confirmar = true, cor = CorGrupo.PAVAO).mudaPlano)
    }

    @Test
    fun `plano concluido nao recebe lista nova por cima`() {
        val feito = plano.copy(itens = plano.itens.map { it.copy(estado = EstadoItem.CRIADO) })
        val novo = aplicarAjuste(feito, AjusteLote(novos = listOf(NovoItem(titulo = "X", dia = hoje() + 3, horario = "10:00"))), grupos)!!
        assertEquals(1, novo.itens.size)
        assertEquals(1, novo.itens[0].n)
    }

    @Test
    fun `contexto leva a lista e a ultima pergunta para a IA`() {
        val texto = contextoDoPlano(plano, grupos)
        assertTrue("item 5: \"Dra. CRIATIANE" in texto)
        assertTrue("Última pergunta feita à pessoa: Quer que eu organize todos no grupo Consultas?" in texto)
    }

    // ---------- Conferência ----------

    @Test
    fun `aponta conflito entre itens da propria lista`() {
        val analises = analisarPlano(plano, emptyList(), emptyList(), null)
        // 01/12: 16:12 com 1h de padrão passa das 16:40
        assertTrue(analises.getValue(3).conflitos.any { "BRUNA" in it })
        assertTrue(analises.getValue(4).conflitos.any { "MAYARA" in it })
        assertTrue(analises.getValue(1).conflitos.none { "Coincide" in it })
    }

    @Test
    fun `conflito some quando a duracao deixa de sobrepor`() {
        val p = aplicarAjuste(plano, AjusteLote(duracaoMin = 20), grupos)!!
        assertTrue(analisarPlano(p, emptyList(), emptyList(), null).getValue(3).conflitos.none { "Coincide" in it })
    }

    @Test
    fun `aponta conflito com evento do Folgui e com a Google Agenda`() {
        val dia = hoje() + 10
        val p = PlanoLote(listOf(item(1, "Dentista", dia, "10:00")))
        val noFolgui = evento(5, "Almoço", dia, "10:30")
        val inicio = momentoEm(dia, "10:15")
        val naAgenda = CompromissoAgenda("Reunião", inicio, inicio + 1_800_000, false, "Trabalho", eventoId = 70)
        val a = analisarPlano(p, listOf(noFolgui), listOf(naAgenda), null).getValue(1)
        assertTrue(a.conflitos.any { "Almoço" in it })
        assertTrue(a.conflitos.any { "Reunião" in it })
    }

    @Test
    fun `evento do Folgui que tambem esta na agenda conta uma vez so`() {
        val dia = hoje() + 10
        val p = PlanoLote(listOf(item(1, "Dentista", dia, "10:00")))
        val nosso = evento(5, "Almoço", dia, "10:30").copy(agendaId = 70)
        val inicio = momentoEm(dia, "10:30")
        val copia = CompromissoAgenda("Almoço", inicio, inicio + 3_600_000, false, "Agenda", eventoId = 70)
        assertEquals(1, analisarPlano(p, listOf(nosso), listOf(copia), null).getValue(1).conflitos.size)
    }

    @Test
    fun `evento de dia inteiro nao conta como conflito`() {
        val dia = hoje() + 10
        val p = PlanoLote(listOf(item(1, "Dentista", dia, "10:00")))
        val aniversario = CompromissoAgenda("Aniversário", momentoEm(dia, "00:00"), momentoEm(dia + 1, "00:00"), true, "Agenda", eventoId = 9)
        assertTrue(analisarPlano(p, emptyList(), listOf(aniversario), null).getValue(1).conflitos.isEmpty())
    }

    @Test
    fun `aponta sono e trabalho pela escala`() {
        val dia = hoje() + 10
        val voce = Pessoa(
            id = Pessoa.ID_VOCE,
            nome = "Eu",
            escala = TipoEscala.ESCALA_12X36,
            inicioCiclo = dia,
            padrao = listOf(Turno(8 * 60, 18 * 60), null),
            sono = Turno(23 * 60, 7 * 60)
        )
        val p = PlanoLote(listOf(item(1, "A", dia, "10:00"), item(2, "B", dia + 1, "03:00")))
        val a = analisarPlano(p, emptyList(), emptyList(), voce)
        assertTrue(a.getValue(1).conflitos.any { "trabalhando" in it })
        assertTrue(a.getValue(2).conflitos.any { "dormindo" in it })
    }

    @Test
    fun `data passada aparece como problema`() {
        val p = PlanoLote(listOf(item(1, "A", hoje() - 3, "10:00")))
        assertTrue(analisarPlano(p, emptyList(), emptyList(), null).getValue(1).conflitos.any { "passou" in it })
    }

    // ---------- Duplicados ----------

    @Test
    fun `repetir o pedido nao cria de novo o que esta no Folgui`() {
        val dia = hoje() + 10
        val p = PlanoLote(listOf(item(1, "Dra. ANA", dia, "10:00"), item(2, "Dra. BIA", dia + 1, "10:00")))
        val existente = evento(5, "dra. ana", dia, "10:00")
        val a = analisarPlano(p, listOf(existente), emptyList(), null)
        assertTrue(a.getValue(1).repetido)
        assertTrue("o repetido não conflita consigo mesmo", a.getValue(1).conflitos.isEmpty())
        assertEquals(listOf(2), itensParaCriar(p, a).map { it.n })
    }

    @Test
    fun `reconhece repetido que so esta na Google Agenda`() {
        val dia = hoje() + 10
        val p = PlanoLote(listOf(item(1, "Dra. ANA", dia, "10:00")))
        val inicio = momentoEm(dia, "10:00")
        val naAgenda = CompromissoAgenda("Dra. ANA", inicio, inicio + 3_600_000, false, "Agenda", eventoId = 3)
        assertTrue(itensParaCriar(p, analisarPlano(p, emptyList(), listOf(naAgenda), null)).isEmpty())
    }

    @Test
    fun `mesmo profissional em outro dia nao e repetido`() {
        val a = analisarPlano(plano, emptyList(), emptyList(), null)
        assertTrue(a.values.none { it.repetido })
    }

    @Test
    fun `pessoa pode mandar criar mesmo repetido`() {
        val dia = hoje() + 10
        val p = PlanoLote(listOf(item(1, "Dra. ANA", dia, "10:00").copy(permitirRepetido = true)))
        val a = analisarPlano(p, listOf(evento(5, "Dra. ANA", dia, "10:00")), emptyList(), null)
        assertEquals(1, itensParaCriar(p, a).size)
    }

    @Test
    fun `avisa quando todos ja existem`() {
        val a = plano.itens.associate { it.n to AnaliseItem(repetido = true) }
        assertTrue("Todos já estão" in perguntaDoPlano(plano, grupos, 8, a))
    }

    // ---------- Criação ----------

    @Test
    fun `cria os oito com grupo, cor herdada e dois avisos`() = runBlocking<Unit> {
        val criados = mutableListOf<Evento>()
        val final = executarLote(pronto, pronto.itens, grupoId = 1, criar = { criados.add(it); ResultadoCriacao() })
        assertEquals(8, criados.size)
        assertTrue(final.concluido)
        assertTrue(criados.all { it.grupoId == 1 && it.avisos == listOf(1440, 60) })
        assertEquals("Pronto! 8 eventos na sua agenda no grupo Consultas, com aviso 1 dia antes e 1 hora antes.", resumoDoLote(final))
    }

    @Test
    fun `sem duracao informada usa a padrao, nao fica sem fim`() {
        assertEquals(DURACAO_PADRAO_LOTE, eventoDoItem(plano.itens[0], plano).duracaoMin)
    }

    @Test
    fun `falha em um diz qual falhou e a nova tentativa so refaz os pendentes`() = runBlocking<Unit> {
        var quebrar = true
        val tentativas = mutableListOf<String>()
        val criar: suspend (Evento) -> ResultadoCriacao = { e ->
            tentativas.add(e.horario + e.dia)
            if (quebrar && e.horario == "09:45") ResultadoCriacao("A agenda recusou este evento") else ResultadoCriacao()
        }
        val primeira = executarLote(pronto, pronto.itens, 1, criar)
        assertFalse(primeira.concluido)
        assertEquals(listOf(5), primeira.pendentes.map { it.n })
        val resumo = resumoDoLote(primeira)
        assertTrue("Criei 7 de 8" in resumo)
        assertTrue("Dra. CRIATIANE BIVAR CAVALCANTI DE OLIVEIRA (psiquiatra): A agenda recusou este evento." in resumo)

        quebrar = false
        tentativas.clear()
        val segunda = executarLote(primeira, itensParaCriar(primeira, emptyMap()), 1, criar)
        assertEquals("só o pendente é refeito", 1, tentativas.size)
        assertTrue(segunda.concluido)
    }

    @Test
    fun `excecao em um item nao derruba os outros`() = runBlocking<Unit> {
        val final = executarLote(pronto, pronto.itens, 1, { e ->
            if (e.horario == "12:00") error("provedor quebrou")
            ResultadoCriacao()
        })
        assertEquals(1, final.itens.count { it.estado == EstadoItem.FALHOU })
        assertEquals(7, final.itens.count { it.estado == EstadoItem.CRIADO })
    }

    @Test
    fun `criar duas vezes o mesmo plano nao duplica`() = runBlocking<Unit> {
        var chamadas = 0
        val criar: suspend (Evento) -> ResultadoCriacao = { chamadas++; ResultadoCriacao() }
        val final = executarLote(pronto, pronto.itens, 1, criar)
        executarLote(final, final.itens, 1, criar)
        assertEquals(8, chamadas)
    }

    @Test
    fun `progresso e informado item a item`() = runBlocking<Unit> {
        var passos = 0
        executarLote(pronto, pronto.itens, 1, { ResultadoCriacao() }, { passos++ })
        assertEquals(8, passos)
    }

    @Test
    fun `plano sobrevive a ida e volta em texto`() {
        val p = aplicarAjuste(pronto, AjusteLote(avisos = listOf(30), itensAvisos = listOf(2), duracaoMin = 45, itensDuracao = listOf(1)), grupos)!!
        assertEquals(p, textoParaPlano(planoParaTexto(p)))
        assertEquals(plano, textoParaPlano(planoParaTexto(plano)))
        assertNull(textoParaPlano("quebrado"))
    }
}
