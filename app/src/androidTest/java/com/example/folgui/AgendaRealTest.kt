package com.example.folgui

import android.Manifest
import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Roda no provedor de calendário de verdade do Android, com uma agenda de teste que tem as
 * mesmas 11 chaves de cor do Google. Não chega ao servidor do Google: isso é da sincronização.
 */
@RunWith(AndroidJUnit4::class)
class AgendaRealTest {

    @get:Rule
    val permissoes: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val conta = Account("folgui-teste", CalendarContract.ACCOUNT_TYPE_LOCAL)
    private val resolver get() = context.contentResolver

    private lateinit var repo: FolguiRepository
    private lateinit var agenda: AgendaRepository
    private lateinit var servico: ServicoAgenda
    private var calendarioId = 0L

    private fun comoSincronizador(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, conta.name)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, conta.type)
        .build()

    @Before
    fun preparar() {
        limparAgenda()
        context.deleteDatabase("folgui.db")
        repo = FolguiRepository(context)
        agenda = AgendaRepository(context)
        servico = ServicoAgenda(context, repo, agenda)

        calendarioId = ContentUris.parseId(
            resolver.insert(
                comoSincronizador(CalendarContract.Calendars.CONTENT_URI),
                ContentValues().apply {
                    put(CalendarContract.Calendars.ACCOUNT_NAME, conta.name)
                    put(CalendarContract.Calendars.ACCOUNT_TYPE, conta.type)
                    put(CalendarContract.Calendars.NAME, "Folgui teste")
                    put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Folgui teste")
                    put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF0000FF.toInt())
                    put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                    put(CalendarContract.Calendars.OWNER_ACCOUNT, conta.name)
                    put(CalendarContract.Calendars.VISIBLE, 1)
                    put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                    put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "America/Sao_Paulo")
                    put(CalendarContract.Calendars.ALLOWED_REMINDERS, "0,1,2")
                }
            )!!
        )
        CorGrupo.entries.forEach { c ->
            resolver.insert(
                comoSincronizador(CalendarContract.Colors.CONTENT_URI),
                ContentValues().apply {
                    put(CalendarContract.Colors.ACCOUNT_NAME, conta.name)
                    put(CalendarContract.Colors.ACCOUNT_TYPE, conta.type)
                    put(CalendarContract.Colors.COLOR_TYPE, CalendarContract.Colors.TYPE_EVENT)
                    put(CalendarContract.Colors.COLOR_KEY, c.id.toString())
                    put(CalendarContract.Colors.COLOR, c.argb.toInt())
                }
            )
        }
    }

    @After
    fun limparAgenda() {
        resolver.delete(
            comoSincronizador(CalendarContract.Calendars.CONTENT_URI),
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ?",
            arrayOf(conta.name)
        )
        resolver.delete(
            comoSincronizador(CalendarContract.Colors.CONTENT_URI),
            "${CalendarContract.Colors.ACCOUNT_NAME} = ? AND ${CalendarContract.Colors.ACCOUNT_TYPE} = ?",
            arrayOf(conta.name, conta.type)
        )
    }

    private data class Linha(val titulo: String, val inicio: Long, val fim: Long, val chave: String?, val cor: Int?, val descricao: String, val fuso: String)

    private fun ler(id: Long): Linha? = resolver.query(
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
        arrayOf(
            CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_COLOR_KEY, CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.DESCRIPTION, CalendarContract.Events.EVENT_TIMEZONE
        ),
        null, null, null
    )?.use { c ->
        if (!c.moveToFirst()) null
        else Linha(
            c.getString(0), c.getLong(1), c.getLong(2), c.getString(3),
            if (c.isNull(4)) null else c.getInt(4), c.getString(5).orEmpty(), c.getString(6)
        )
    }

    private fun lembretes(id: Long): List<Int> = resolver.query(
        CalendarContract.Reminders.CONTENT_URI,
        arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
        "${CalendarContract.Reminders.EVENT_ID} = ?",
        arrayOf(id.toString()),
        null
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                assertEquals(CalendarContract.Reminders.METHOD_ALERT, c.getInt(1))
                add(c.getInt(0))
            }
        }
    }.orEmpty().sortedDescending()

    /** Apaga como o Google faria na sincronização: a linha some de vez. */
    private fun apagarDireto(id: Long) {
        resolver.delete(comoSincronizador(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)), null, null)
    }

    private val dia = hoje() + 20
    private fun evento(titulo: String, d: Int = dia, horario: String = "16:12", grupo: Int = 0) = Evento(
        titulo = titulo, dia = d, horario = horario, local = "", antecedenciaMin = -1, duracaoMin = 60,
        participantes = emptySet(), grupoId = grupo
    ).comAvisos(listOf(1440, 60))

    private fun plano(): PlanoLote = PlanoLote(
        itens = listOf(
            "2026-11-24" to "16:12", "2026-11-28" to "12:00", "2026-12-01" to "16:12", "2026-12-01" to "16:40",
            "2026-12-07" to "09:45", "2026-12-08" to "16:12", "2026-12-09" to "13:48", "2027-01-05" to "08:00"
        ).mapIndexed { i, (d, h) -> ItemLote(n = i + 1, titulo = "Consulta ${i + 1}", dia = isoParaDia(d)!!, horario = h, profissional = "Dra. ANA", categoria = "psicóloga") },
        grupoDecidido = true, grupoNome = "Consultas", cor = CorGrupo.TOMATE, corDecidida = true, avisos = listOf(1440, 60)
    )

    @Test
    fun oitoEventosComGrupoCorEDoisAvisos() = runBlocking<Unit> {
        val grupo = Grupo(repo.inserirGrupo(Grupo(nome = "Consultas", cor = CorGrupo.TOMATE)), "Consultas", CorGrupo.TOMATE)
        val p = plano()
        val final = executarLote(p, p.itens, grupo.id, { servico.criar(it, grupo.cor) })

        assertTrue(final.itens.joinToString { it.erro }, final.concluido)
        val eventos = repo.listarEventos()
        assertEquals(8, eventos.size)
        eventos.forEach { e ->
            assertTrue(e.agendaId > 0)
            assertEquals(grupo.id, e.grupoId)
            assertTrue(e.avisoNaAgenda)
            val l = ler(e.agendaId)!!
            assertEquals("11", l.chave)
            assertEquals(CorGrupo.TOMATE.argb.toInt(), l.cor)
            assertEquals(e.momento, l.inicio)
            assertEquals(e.momento + 3_600_000, l.fim)
            assertEquals(listOf(1440, 60), lembretes(e.agendaId))
            assertTrue("Dra. ANA" in l.descricao)
        }
        // O último vira o ano e cai na hora certa no fuso do aparelho
        val ultimo = eventos.first { it.titulo == "Consulta 8" }
        assertEquals("08:00", horarioDoMomento(ler(ultimo.agendaId)!!.inicio))
        assertEquals("2027-01-05", diaParaIso(diaDoMomento(ler(ultimo.agendaId)!!.inicio)))
    }

    @Test
    fun eventoSemGrupoFicaComCorPadrao() {
        val gravado = agenda.salvar(evento("Sem grupo"), 0, null)
        val l = ler(gravado.id!!)!!
        assertNull(l.chave)
        assertNull(l.cor)
    }

    @Test
    fun novaConsultaHerdaACorDoGrupo() = runBlocking<Unit> {
        val grupo = Grupo(repo.inserirGrupo(Grupo(nome = "Consultas", cor = CorGrupo.TOMATE)), "Consultas", CorGrupo.TOMATE)
        servico.criar(evento("Nova", grupo = grupo.id), grupo.cor)
        assertEquals("11", ler(repo.listarEventos().single().agendaId)!!.chave)
    }

    @Test
    fun mudarACorDoGrupoRepintaOsEventos() = runBlocking<Unit> {
        val grupo = Grupo(repo.inserirGrupo(Grupo(nome = "Consultas", cor = CorGrupo.TOMATE)), "Consultas", CorGrupo.TOMATE)
        servico.criar(evento("A", grupo = grupo.id), grupo.cor)
        servico.criar(evento("B", dia + 1, grupo = grupo.id), grupo.cor)
        val falhas = servico.pintar(repo.listarEventos(), CorGrupo.UVA)
        assertTrue(falhas.isEmpty())
        repo.listarEventos().forEach { assertEquals("3", ler(it.agendaId)!!.chave) }
    }

    @Test
    fun moverEntreGruposETirarDoGrupo() = runBlocking<Unit> {
        val a = Grupo(repo.inserirGrupo(Grupo(nome = "A", cor = CorGrupo.TOMATE)), "A", CorGrupo.TOMATE)
        val b = Grupo(repo.inserirGrupo(Grupo(nome = "B", cor = CorGrupo.PAVAO)), "B", CorGrupo.PAVAO)
        servico.criar(evento("X", grupo = a.id), a.cor)
        var e = repo.listarEventos().single()

        servico.mover(listOf(e), b)
        e = repo.listarEventos().single()
        assertEquals(b.id, e.grupoId)
        assertEquals("7", ler(e.agendaId)!!.chave)

        servico.mover(listOf(e), null)
        e = repo.listarEventos().single()
        assertEquals(0, e.grupoId)
        val l = ler(e.agendaId)!!
        assertNull("sem grupo volta à cor padrão", l.chave)
        assertNull(l.cor)
    }

    @Test
    fun excluirGrupoMantemOsEventos() = runBlocking<Unit> {
        val g = Grupo(repo.inserirGrupo(Grupo(nome = "A", cor = CorGrupo.TOMATE)), "A", CorGrupo.TOMATE)
        servico.criar(evento("X", grupo = g.id), g.cor)
        repo.excluirGrupo(g.id)
        assertTrue(repo.listarGrupos().isEmpty())
        assertEquals(0, repo.listarEventos().single().grupoId)
        Unit
    }

    @Test
    fun editarEventoTrocaOsLembretesSemDuplicar() = runBlocking<Unit> {
        servico.criar(evento("X"), null)
        val e = repo.listarEventos().single()
        agenda.salvar(e.comAvisos(listOf(30)), e.agendaId, null)
        assertEquals(listOf(30), lembretes(e.agendaId))
        agenda.salvar(e.comAvisos(emptyList()), e.agendaId, null)
        assertTrue(lembretes(e.agendaId).isEmpty())
    }

    @Test
    fun editarNaoApagaADescricaoDaAgenda() = runBlocking<Unit> {
        servico.criar(evento("X").copy(detalhes = "Profissional: Dra. ANA"), null)
        val e = repo.listarEventos().single()
        agenda.salvar(e.copy(titulo = "Y"), e.agendaId, null)
        val l = ler(e.agendaId)!!
        assertEquals("Y", l.titulo)
        assertTrue("Dra. ANA" in l.descricao)
    }

    @Test
    fun conciliaOQueMudouDiretoNaAgenda() = runBlocking<Unit> {
        val g = Grupo(repo.inserirGrupo(Grupo(nome = "A", cor = CorGrupo.TOMATE)), "A", CorGrupo.TOMATE)
        servico.criar(evento("X", grupo = g.id), g.cor)
        val e = repo.listarEventos().single()
        val inicio = momentoEm(dia + 2, "09:30")
        resolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.agendaId),
            ContentValues().apply {
                put(CalendarContract.Events.TITLE, "Mudado no Google")
                put(CalendarContract.Events.DTSTART, inicio)
                put(CalendarContract.Events.DTEND, inicio + 45 * 60_000L)
                put(CalendarContract.Events.EVENT_COLOR_KEY, "3")
            },
            null, null
        )
        assertTrue(servico.conciliar())
        val novo = repo.listarEventos().single()
        assertEquals("Mudado no Google", novo.titulo)
        assertEquals(dia + 2, novo.dia)
        assertEquals("09:30", novo.horario)
        assertEquals(45, novo.duracaoMin)
        assertEquals("a cor é do grupo", "11", ler(e.agendaId)!!.chave)
    }

    @Test
    fun eventoApagadoNaAgendaFicaMarcadoEPodeVoltar() = runBlocking<Unit> {
        val g = Grupo(repo.inserirGrupo(Grupo(nome = "A", cor = CorGrupo.TOMATE)), "A", CorGrupo.TOMATE)
        servico.criar(evento("X", grupo = g.id), g.cor)
        val e = repo.listarEventos().single()

        apagarDireto(e.agendaId)
        assertTrue(servico.conciliar())
        val marcado = repo.listarEventos().single()
        assertTrue(marcado.foraDaAgenda)
        assertEquals(g.id, marcado.grupoId)

        assertTrue(servico.restaurar(marcado, g.cor))
        val volta = repo.listarEventos().single()
        assertFalse(volta.foraDaAgenda)
        assertTrue(volta.agendaId != e.agendaId)
        assertEquals("11", ler(volta.agendaId)!!.chave)
        assertEquals(listOf(1440, 60), lembretes(volta.agendaId))
    }

    @Test
    fun repetirOPedidoEReconhecidoNosDadosReais() = runBlocking<Unit> {
        val p = plano()
        executarLote(p, p.itens, 0, { servico.criar(it, null) })

        val dias = p.itens.map { it.dia }
        val compromissos = agenda.buscar(dias.min(), dias.max()).values.flatten()
        val analises = analisarPlano(p, repo.listarEventos(), compromissos, null)
        assertTrue(itensParaCriar(p, analises).isEmpty())

        // O mesmo pedido em um aparelho que só tem a agenda, sem os eventos no Folgui
        val soAgenda = analisarPlano(p, emptyList(), compromissos, null)
        assertTrue(itensParaCriar(p, soAgenda).isEmpty())
    }

    @Test
    fun importaEventoCriadoDiretoNaAgendaSemPerderOsLembretes() = runBlocking<Unit> {
        val g = Grupo(repo.inserirGrupo(Grupo(nome = "A", cor = CorGrupo.UVA)), "A", CorGrupo.UVA)
        val id = agenda.salvar(evento("Externo").comAvisos(listOf(120)), 0, null).id!!
        val compromisso = agenda.buscar(dia, dia).values.flatten().first { it.eventoId == id }
        assertTrue(compromisso.editavel)
        assertFalse(compromisso.recorrente)

        assertTrue(servico.importar(compromisso, g))
        val e = repo.listarEventos().single()
        assertEquals(id, e.agendaId)
        assertEquals(listOf(120), e.avisos)
        assertEquals("3", ler(id)!!.chave)
        assertEquals(listOf(120), lembretes(id))
    }

    @Test
    fun migracaoDaVersao7MantemOsEventos() {
        context.deleteDatabase("folgui.db")
        val antigo = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("folgui.db"), null)
        antigo.execSQL("CREATE TABLE pessoa (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL)")
        antigo.execSQL(
            "CREATE TABLE evento (id INTEGER PRIMARY KEY AUTOINCREMENT, titulo TEXT NOT NULL, data TEXT NOT NULL, " +
                "horario TEXT NOT NULL, local_evento TEXT NOT NULL, antecedencia_min INTEGER NOT NULL, " +
                "duracao_min INTEGER NOT NULL DEFAULT 120, participantes TEXT NOT NULL DEFAULT '*', " +
                "time_millis INTEGER NOT NULL, agenda_id INTEGER NOT NULL DEFAULT 0)"
        )
        antigo.execSQL("INSERT INTO evento (titulo, data, horario, local_evento, antecedencia_min, time_millis, agenda_id) VALUES ('Velho', '${diaParaTexto(dia)}', '10:00', '', 60, 0, 5)")
        antigo.version = 7
        antigo.close()

        val novo = FolguiRepository(context)
        val e = novo.listarEventos().single()
        assertEquals("Velho", e.titulo)
        assertEquals(60, e.antecedenciaMin)
        assertEquals(5L, e.agendaId)
        assertEquals(0, e.grupoId)
        assertFalse("evento antigo segue avisado pelo Folgui", e.avisoNaAgenda)
        assertNotNull(novo.listarGrupos())
    }

    @Test
    fun semPermissaoOuSemAgendaOErroEClaro() {
        limparAgenda()
        val r = agenda.salvar(evento("X"), 0, null)
        // O emulador pode ter outras agendas; sem nenhuma, a falha diz isso
        if (r.id == null) assertEquals(FalhaAgenda.SEM_AGENDA, r.falha) else agenda.excluir(r.id!!)
    }
}
