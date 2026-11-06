package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes dos grupos: cores da agenda, avisos, conciliação e comandos para a IA
class GruposTest {

    private val dia = hoje() + 20
    private val consultas = Grupo(1, "Consultas", CorGrupo.TOMATE)
    private val estudos = Grupo(2, "Estudos", CorGrupo.PAVAO)
    private val grupos = listOf(consultas, estudos)
    private val chaves = CorGrupo.entries.associateWith { it.id.toString() }

    private val evento = Evento(
        id = 3, titulo = "Dentista", dia = dia, horario = "10:00", local = "", antecedenciaMin = 60, duracaoMin = 60,
        participantes = emptySet(), agendaId = 90, grupoId = 1, avisoNaAgenda = true
    )

    private fun lido(
        titulo: String = "Dentista",
        inicio: Long = evento.momento,
        duracao: Int = 60,
        local: String = "",
        cor: String? = "11",
        avisos: List<Int> = listOf(60)
    ) = EventoNaAgenda(90, titulo, inicio, inicio + duracao * 60_000L, local, cor, avisos, chaves)

    private fun conciliar(l: EventoNaAgenda?, e: Evento = evento) =
        conciliarAgenda(listOf(e), if (l == null) emptyMap() else mapOf(90L to l), grupos)

    // ---------- Cores ----------

    @Test
    fun `paleta tem as onze cores da agenda com ids de 1 a 11`() {
        assertEquals((1..11).toList(), CorGrupo.entries.map { it.id })
    }

    @Test
    fun `entende a cor pelo nome falado`() {
        assertEquals(CorGrupo.TOMATE, CorGrupo.porNome("vermelho"))
        assertEquals(CorGrupo.UVA, CorGrupo.porNome("Roxo"))
        assertEquals(CorGrupo.PAVAO, CorGrupo.porNome("azul"))
        assertEquals(CorGrupo.MANJERICAO, CorGrupo.porNome("manjericão"))
        assertNull(CorGrupo.porNome("fúcsia"))
    }

    @Test
    fun `usa a chave do Google quando a conta tem`() {
        val cores = listOf(CorDaConta("1", 0xFFA4BDFC.toInt()), CorDaConta("11", 0xFFDC2127.toInt()))
        assertEquals("11", chaveDaCor(CorGrupo.TOMATE, cores))
    }

    @Test
    fun `em outra conta usa a cor mais parecida`() {
        val cores = listOf(CorDaConta("azul", 0xFF0000FF.toInt()), CorDaConta("vermelho", 0xFFFF0000.toInt()))
        assertEquals("vermelho", chaveDaCor(CorGrupo.TOMATE, cores))
    }

    @Test
    fun `conta sem cores nao recebe chave`() {
        assertNull(chaveDaCor(CorGrupo.TOMATE, emptyList()))
    }

    // ---------- Grupos ----------

    @Test
    fun `acha o grupo sem ligar para acento e maiuscula`() {
        assertEquals(consultas, grupoPorNome(grupos, " CONSULTAS "))
        assertEquals("Saude", normalizarTexto("Saúde").replaceFirstChar { it.uppercase() })
    }

    @Test
    fun `nome vazio ou repetido nao vale, a nao ser o do proprio grupo`() {
        assertNotNull(erroNomeGrupo("  ", grupos))
        assertNotNull(erroNomeGrupo("consultas", grupos))
        assertNull(erroNomeGrupo("consultas", grupos, ignorarId = 1))
        assertNull(erroNomeGrupo("Trabalho", grupos))
    }

    @Test
    fun `sugere uma cor que nenhum grupo usa`() {
        assertFalse(corSugerida(grupos) in grupos.map { it.cor })
    }

    @Test
    fun `evento sem grupo nao tem grupo nem lista`() {
        assertNull(grupoDoEvento(evento.copy(grupoId = 0), grupos))
        assertTrue(eventosDoGrupo(listOf(evento.copy(grupoId = 0)), 0).isEmpty())
    }

    @Test
    fun `grupos vao para a conta e voltam, junto com os eventos`() {
        val perfil = Perfil("Ana", TipoEscala.SEMANAL, dia, null, null, listOf(Turno(480, 1020)), 0, null)
        val e = Evento(
            id = 3, titulo = "Dentista", dia = dia, horario = "10:00", local = "", antecedenciaMin = 1440,
            avisosExtras = listOf(60), participantes = emptySet(), grupoId = 1, detalhes = "Profissional: Dra. ANA"
        )
        val dados = DadosNuvem(perfil, emptyMap(), emptyList(), listOf(e), grupos)
        val volta = textoParaDados(dadosParaTexto(dados))!!
        assertEquals(grupos, volta.grupos)
        assertEquals(e, volta.eventos[0])
    }

    @Test
    fun `copia antiga, sem grupos, continua abrindo`() {
        val perfil = Perfil("Ana", TipoEscala.SEMANAL, dia, null, null, listOf(Turno(480, 1020)), 0, null)
        val antigo = dadosParaTexto(DadosNuvem(perfil, emptyMap(), emptyList(), emptyList()))
            .replace(",\"grupos\":[]", "")
        assertTrue(textoParaDados(antigo)!!.grupos.isEmpty())
    }

    // ---------- Avisos ----------

    @Test
    fun `dois avisos ficam em ordem, sem repetir`() {
        val e = evento.comAvisos(listOf(60, 1440, 60))
        assertEquals(listOf(1440, 60), e.avisos)
        assertEquals(1440, e.antecedenciaMin)
        assertEquals(listOf(60), e.avisosExtras)
        assertEquals("1 dia antes e 1 hora antes", rotuloAvisos(e.avisos))
    }

    @Test
    fun `sem avisos vira sem lembrete`() {
        val e = evento.comAvisos(emptyList())
        assertEquals(-1, e.antecedenciaMin)
        assertNull(e.momentoLembrete)
        assertEquals("Sem lembrete", rotuloAvisos(e.avisos))
    }

    @Test
    fun `respeita o limite da Google Agenda`() {
        assertEquals(5, normalizarAvisos(listOf(0, 10, 20, 30, 40, 50, 60)).size)
        assertEquals(listOf(40320), normalizarAvisos(listOf(50000, 40320, -1)))
    }

    @Test
    fun `aviso que ja passou nao conta como proximo`() {
        val amanha = evento.copy(dia = hoje() + 1, horario = "23:59").comAvisos(listOf(10080, 60))
        assertEquals(1, amanha.proximosLembretes.size)
    }

    @Test
    fun `rotulos de aviso e duracao`() {
        assertEquals("2 dias antes", rotuloLembrete(2880))
        assertEquals("3 horas antes", rotuloLembrete(180))
        assertEquals("45 min antes", rotuloLembrete(45))
        assertEquals("45 min", rotuloDuracao(45))
        assertEquals("1h", rotuloDuracao(60))
        assertEquals("1h30", rotuloDuracao(90))
    }

    // ---------- Conciliação ----------

    @Test
    fun `nada mudou, nada a fazer`() {
        assertEquals(Conciliacao(), conciliar(lido()))
    }

    @Test
    fun `apagado na agenda e so marcado, nunca excluido`() {
        assertEquals(listOf(evento), conciliar(null).apagados)
    }

    @Test
    fun `quem ja esta marcado nao e contado de novo e volta se reaparecer`() {
        val fora = evento.copy(foraDaAgenda = true)
        assertTrue(conciliar(null, fora).apagados.isEmpty())
        assertEquals(listOf(fora), conciliar(lido(), fora).reapareceram)
    }

    @Test
    fun `traz titulo, horario, duracao e local mudados direto na agenda`() {
        val inicio = momentoEm(dia + 1, "15:30")
        val novo = conciliar(lido("Dentista novo", inicio, 90, "Centro")).atualizados.single()
        assertEquals("Dentista novo", novo.titulo)
        assertEquals(dia + 1, novo.dia)
        assertEquals("15:30", novo.horario)
        assertEquals(90, novo.duracaoMin)
        assertEquals("Centro", novo.local)
    }

    @Test
    fun `evento sem fim nao ganha fim so porque a agenda reserva uma hora`() {
        assertTrue(conciliar(lido(), evento.copy(duracaoMin = SEM_FIM)).atualizados.isEmpty())
    }

    @Test
    fun `traz lembretes mudados direto na agenda`() {
        assertEquals(listOf(30), conciliar(lido(avisos = listOf(30))).atualizados.single().avisos)
    }

    @Test
    fun `evento antigo, com o aviso so no Folgui, nao perde o lembrete`() {
        assertTrue(conciliar(lido(avisos = emptyList()), evento.copy(avisoNaAgenda = false)).atualizados.isEmpty())
    }

    @Test
    fun `evento do grupo que perdeu a cor recebe a cor de novo`() {
        assertEquals(listOf(evento), conciliar(lido(cor = "3")).recolorir)
        assertEquals(listOf(evento), conciliar(lido(cor = null)).recolorir)
    }

    @Test
    fun `evento sem grupo nunca e pintado`() {
        assertTrue(conciliar(lido(cor = "3"), evento.copy(grupoId = 0)).recolorir.isEmpty())
        assertTrue(conciliar(lido(cor = null), evento.copy(grupoId = 0)).recolorir.isEmpty())
    }

    @Test
    fun `conta sem cores nao tenta pintar`() {
        assertTrue(conciliar(lido(cor = null).copy(chaves = emptyMap())).recolorir.isEmpty())
    }

    @Test
    fun `evento que nao esta na agenda fica de fora da conciliacao`() {
        assertEquals(Conciliacao(), conciliarAgenda(listOf(evento.copy(agendaId = 0)), emptyMap(), grupos))
    }

    // ---------- Comandos para a IA ----------

    private fun acoes(json: String) = interpretarResposta("""{"resposta":"Confira","acoes":[$json]}""").mensagem.acoes

    private val eu = Pessoa(id = Pessoa.ID_VOCE, nome = "Eu", escala = TipoEscala.SEMANAL, inicioCiclo = dia, padrao = listOf(null))

    @Test
    fun `IA cria grupo e muda a cor com aviso do que vai repintar`() {
        assertEquals(CorGrupo.UVA, acoes("""{"tipo":"criar_grupo","titulo":"Saúde","cor":3}""").single().cor)
        val mudar = acoes("""{"tipo":"editar_grupo","grupo_id":1,"cor":3}""")
        val (validas, avisos) = verificarAcoes(mudar, listOf(eu), listOf(evento), grupos)
        assertEquals(1, validas.size)
        assertEquals("1 evento do grupo Consultas vão mudar para a cor roxa.", avisos.single())
        assertEquals("Grupo Consultas: cor roxa, com os eventos dele", descreverAcao(validas[0], listOf(eu), listOf(evento), grupos))
    }

    @Test
    fun `cor fora da paleta nao vale`() {
        assertTrue(acoes("""{"tipo":"criar_grupo","titulo":"Saúde","cor":42}""").isEmpty())
    }

    @Test
    fun `IA nao cria grupo repetido nem usa grupo ou evento que nao existem`() {
        val a = acoes(
            """{"tipo":"criar_grupo","titulo":"consultas","cor":3},
               {"tipo":"excluir_grupo","grupo_id":99},
               {"tipo":"mover_eventos","evento_ids":[3,44],"grupo_id":1}"""
        )
        assertEquals(3, a.size)
        assertTrue(verificarAcoes(a, listOf(eu), listOf(evento), grupos).first.isEmpty())
    }

    @Test
    fun `IA move eventos e tira do grupo`() {
        val mover = acoes("""{"tipo":"mover_eventos","evento_ids":[3],"grupo_id":2}""").single()
        assertEquals("Mover 1 evento para o grupo Estudos", descreverAcao(mover, listOf(eu), listOf(evento), grupos))
        val tirar = acoes("""{"tipo":"mover_eventos","evento_ids":[3],"grupo_id":-1}""").single()
        assertEquals("Tirar 1 evento do grupo", descreverAcao(tirar, listOf(eu), listOf(evento), grupos))
    }

    @Test
    fun `IA configura avisos em lote e pode tirar todos`() {
        val a = acoes("""{"tipo":"avisos_eventos","evento_ids":[3],"avisos_min":[60,1440]}""").single()
        assertEquals(listOf(1440, 60), a.avisos)
        assertEquals("Avisos de 1 evento: 1 dia antes e 1 hora antes", descreverAcao(a, listOf(eu), listOf(evento), grupos))
        assertEquals(emptyList<Int>(), acoes("""{"tipo":"avisos_eventos","evento_ids":[3],"avisos_min":[]}""").single().avisos)
    }

    @Test
    fun `IA cria evento no grupo com dois avisos`() {
        val a = acoes("""{"tipo":"criar_evento","titulo":"Exame","dia":"${diaParaIso(dia)}","horario":"09:00","grupo_id":1,"avisos_min":[1440,60]}""").single()
        val e = eventoResultante(a, emptyList())!!
        assertEquals(1, e.grupoId)
        assertEquals(listOf(1440, 60), e.avisos)
    }

    @Test
    fun `editar evento pela IA sem falar de grupo nao tira do grupo`() {
        val a = acoes("""{"tipo":"editar_evento","evento_id":3,"horario":"11:00","grupo_id":0}""").single()
        assertEquals(1, eventoResultante(a, listOf(evento))!!.grupoId)
        val tirar = acoes("""{"tipo":"editar_evento","evento_id":3,"grupo_id":-1}""").single()
        assertEquals(0, eventoResultante(tirar, listOf(evento))!!.grupoId)
    }

    @Test
    fun `contexto da IA mostra grupos e eventos com grupo`() {
        val texto = contextoParaIA(listOf(eu), listOf(evento), emptyMap(), emptyMap(), grupos)
        assertTrue("grupo_id 1: Consultas, cor 11 (vermelha), 1 evento" in texto)
        assertTrue(", grupo Consultas" in texto)
    }
}
