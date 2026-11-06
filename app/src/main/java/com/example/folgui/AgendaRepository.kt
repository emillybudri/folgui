package com.example.folgui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/** Compromisso lido da agenda do celular (Google Agenda, inclusive eventos compartilhados). */
data class CompromissoAgenda(
    val titulo: String,
    val inicio: Long,
    val fim: Long,
    val diaInteiro: Boolean,
    val agenda: String,
    val eventoId: Long = 0,
    val local: String = "",
    /** Repete: não dá para tratar uma ocorrência como um evento só. */
    val recorrente: Boolean = false,
    /** A pessoa pode mudar este evento (agenda compartilhada só para leitura não pode). */
    val editavel: Boolean = true
) {
    val horario: String
        get() = if (diaInteiro) "Dia inteiro" else "${horarioDoMomento(inicio)} – ${horarioDoMomento(fim)}"
}

val PERMISSOES_AGENDA = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

/** Uma agenda do celular em que dá para escrever. */
data class AgendaDoCelular(val id: Long, val principal: Boolean, val google: Boolean)

/** Por que não deu para gravar, com a frase para mostrar à pessoa. */
enum class FalhaAgenda(val mensagem: String) {
    SEM_PERMISSAO("Sem permissão para acessar a agenda"),
    SEM_AGENDA("Nenhuma agenda disponível neste celular"),
    ERRO("A agenda recusou este evento")
}

/** O que aconteceu ao gravar: [id] nulo quer dizer que o evento não foi para a agenda. */
data class ResultadoAgenda(
    val id: Long? = null,
    val falha: FalhaAgenda? = null,
    val avisosGravados: Boolean = false,
    val corAplicada: Boolean = true
)

/** A principal do Google; sem ela, outra do Google e, por último, qualquer agenda do aparelho. */
fun escolherAgenda(agendas: List<AgendaDoCelular>): AgendaDoCelular? =
    agendas.maxByOrNull { (if (it.google) 2 else 0) + (if (it.principal) 1 else 0) }

/** Tira da leitura da agenda o que foi criado pelo Folgui, para o evento não aparecer duas vezes. */
fun semEventosDoFolgui(
    agenda: Map<Int, List<CompromissoAgenda>>,
    eventos: List<Evento>
): Map<Int, List<CompromissoAgenda>> {
    val nossos = eventos.map { it.agendaId }.filter { it > 0 }.toSet()
    return agenda.mapValues { (_, lista) -> lista.filter { it.eventoId !in nossos } }
        .filterValues { it.isNotEmpty() }
}

/**
 * Eventos sem vínculo (vindos de outro aparelho ou enviados à mão) que já estão na agenda,
 * pelo título e horário. Retorna o id do evento e o id de lá.
 */
fun vinculosAgenda(eventos: List<Evento>, agenda: Map<Int, List<CompromissoAgenda>>): Map<Int, Long> {
    val compromissos = agenda.values.flatten().distinctBy { it.eventoId }
    val usados = eventos.map { it.agendaId }.toMutableSet()
    val vinculos = mutableMapOf<Int, Long>()
    eventos.filter { it.agendaId == 0L }.forEach { e ->
        val achado = compromissos.firstOrNull { it.titulo == e.titulo && it.inicio == e.momento && it.eventoId !in usados }
        if (achado != null) {
            usados.add(achado.eventoId)
            vinculos[e.id] = achado.eventoId
        }
    }
    return vinculos
}

// Leitura e escrita na Google Agenda pelo provedor de calendário do Android (consulta com Cursor, como no SQLite)
class AgendaRepository(context: Context) {

    private val appContext = context.applicationContext

    fun temPermissao(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun temEscrita(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Cria o evento na agenda ou, se ele já estiver lá ([agendaId]), atualiza o mesmo evento.
     * Com [cor], o evento recebe a cor do grupo; sem ela, a cor dele não é tocada.
     */
    fun salvar(evento: Evento, agendaId: Long, cor: CorGrupo? = null): ResultadoAgenda {
        if (!temEscrita()) return ResultadoAgenda(falha = FalhaAgenda.SEM_PERMISSAO)
        val valores = ContentValues().apply {
            put(CalendarContract.Events.TITLE, evento.titulo)
            put(CalendarContract.Events.EVENT_LOCATION, evento.local)
            put(CalendarContract.Events.DTSTART, evento.momento)
            put(CalendarContract.Events.DTEND, evento.momento + evento.janela.duracao * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }
        val id = try {
            if (agendaId > 0 && existe(agendaId)) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, agendaId)
                if (appContext.contentResolver.update(uri, valores, null, null) > 0) agendaId else null
            } else {
                // Sem vínculo, ou o evento foi apagado na própria agenda: cria de novo.
                // A descrição só vai na criação, para não apagar o que a pessoa escreveu lá
                val agenda = agendaParaGravar() ?: return ResultadoAgenda(falha = FalhaAgenda.SEM_AGENDA)
                valores.put(CalendarContract.Events.CALENDAR_ID, agenda.id)
                valores.put(CalendarContract.Events.DESCRIPTION, descricaoDoEvento(evento))
                appContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, valores)
                    ?.let(ContentUris::parseId)
            }
        } catch (e: SecurityException) {
            return ResultadoAgenda(falha = FalhaAgenda.SEM_PERMISSAO)
        } catch (e: RuntimeException) {
            null // Cada fabricante tem seu provedor de agenda: uma falha lá não pode derrubar o app
        } ?: return ResultadoAgenda(falha = FalhaAgenda.ERRO)

        return ResultadoAgenda(
            id = id,
            avisosGravados = gravarLembretes(id, evento.avisos),
            corAplicada = cor == null || aplicarCor(id, cor)
        )
    }

    /** Retorna o id na agenda, ou null se não deu para gravar. */
    fun gravar(evento: Evento, agendaId: Long, cor: CorGrupo? = null): Long? = salvar(evento, agendaId, cor).id

    /** Troca os lembretes do evento na agenda pelos da lista. */
    private fun gravarLembretes(agendaId: Long, avisos: List<Int>): Boolean = try {
        val resolver = appContext.contentResolver
        resolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(agendaId.toString())
        )
        avisos.all { minutos ->
            resolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, agendaId)
                    put(CalendarContract.Reminders.MINUTES, minutos)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
            ) != null
        }
    } catch (e: RuntimeException) {
        false
    }

    /**
     * Pinta o evento com a cor do grupo ou, com null, devolve a cor padrão da agenda.
     * Retorna false se a conta não tiver cores de evento ou a agenda recusar.
     */
    fun aplicarCor(agendaId: Long, cor: CorGrupo?): Boolean {
        if (agendaId <= 0 || !temEscrita()) return false
        return try {
            val valores = ContentValues()
            if (cor == null) {
                valores.putNull(CalendarContract.Events.EVENT_COLOR_KEY)
                valores.putNull(CalendarContract.Events.EVENT_COLOR)
            } else {
                val conta = contaDoEvento(agendaId) ?: return false
                val chave = chaveDaCor(cor, coresDaConta(conta)) ?: return false
                valores.put(CalendarContract.Events.EVENT_COLOR_KEY, chave)
            }
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, agendaId)
            appContext.contentResolver.update(uri, valores, null, null) > 0
        } catch (e: RuntimeException) {
            false
        }
    }

    private fun contaDoEvento(agendaId: Long): Pair<String, String>? =
        appContext.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, agendaId),
            arrayOf(CalendarContract.Events.ACCOUNT_NAME, CalendarContract.Events.ACCOUNT_TYPE),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() to c.getString(1).orEmpty() else null }

    /** Cores de evento que a conta oferece: o Google traz as suas pela sincronização. */
    private fun coresDaConta(conta: Pair<String, String>): List<CorDaConta> {
        val cores = mutableListOf<CorDaConta>()
        appContext.contentResolver.query(
            CalendarContract.Colors.CONTENT_URI,
            arrayOf(CalendarContract.Colors.COLOR_KEY, CalendarContract.Colors.COLOR),
            "${CalendarContract.Colors.ACCOUNT_NAME} = ? AND ${CalendarContract.Colors.ACCOUNT_TYPE} = ? AND " +
                "${CalendarContract.Colors.COLOR_TYPE} = ${CalendarContract.Colors.TYPE_EVENT}",
            arrayOf(conta.first, conta.second),
            null
        )?.use { c ->
            while (c.moveToNext()) cores.add(CorDaConta(c.getString(0), c.getInt(1)))
        }
        return cores
    }

    /** A agenda em que os eventos novos vão cair é do Google? Serve para avisar antes de gravar. */
    fun temAgendaGoogle(): Boolean = try {
        temPermissao() && agendaParaGravar()?.google == true
    } catch (e: RuntimeException) {
        false
    }

    /**
     * Estado atual dos eventos com os ids pedidos. Quem não aparece foi apagado da agenda.
     * Retorna null se não deu para ler: nesse caso nada pode ser tratado como apagado.
     */
    suspend fun lerEventos(ids: List<Long>): Map<Long, EventoNaAgenda>? = withContext(Dispatchers.IO) {
        if (!temPermissao()) return@withContext null
        if (ids.isEmpty()) return@withContext emptyMap()
        try {
            val avisos = lerLembretes(ids)
            val chaves = mutableMapOf<Pair<String, String>, Map<CorGrupo, String>>()
            val lidos = mutableMapOf<Long, EventoNaAgenda>()
            val colunas = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.EVENT_COLOR_KEY,
                CalendarContract.Events.ACCOUNT_NAME,
                CalendarContract.Events.ACCOUNT_TYPE
            )
            ids.chunked(LOTE_CONSULTA).forEach { parte ->
                val cursor = appContext.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    colunas,
                    "${CalendarContract.Events._ID} IN (${parte.joinToString(",")}) AND ${CalendarContract.Events.DELETED} = 0",
                    null,
                    null
                ) ?: return@withContext null
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val conta = c.getString(6).orEmpty() to c.getString(7).orEmpty()
                        val id = c.getLong(0)
                        val inicio = c.getLong(2)
                        lidos[id] = EventoNaAgenda(
                            id = id,
                            titulo = c.getString(1).orEmpty(),
                            inicio = inicio,
                            fim = if (c.isNull(3)) inicio else c.getLong(3),
                            local = c.getString(4).orEmpty(),
                            corChave = c.getString(5),
                            avisos = avisos[id].orEmpty(),
                            chaves = chaves.getOrPut(conta) {
                                val cores = coresDaConta(conta)
                                CorGrupo.entries.mapNotNull { cor -> chaveDaCor(cor, cores)?.let { cor to it } }.toMap()
                            }
                        )
                    }
                }
            }
            lidos
        } catch (e: RuntimeException) {
            null
        }
    }

    /** Lembretes de cada evento, do maior para o menor. */
    private fun lerLembretes(ids: List<Long>): Map<Long, List<Int>> {
        val mapa = mutableMapOf<Long, MutableList<Int>>()
        ids.chunked(LOTE_CONSULTA).forEach { parte ->
            appContext.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES),
                "${CalendarContract.Reminders.EVENT_ID} IN (${parte.joinToString(",")})",
                null,
                null
            )?.use { c ->
                while (c.moveToNext()) mapa.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getInt(1))
            }
        }
        return mapa.mapValues { normalizarAvisos(it.value) }
    }

    fun excluir(agendaId: Long) {
        if (agendaId <= 0 || !temEscrita()) return
        try {
            appContext.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, agendaId), null, null
            )
        } catch (e: RuntimeException) {
            // O evento fica na agenda, e o Folgui segue funcionando
        }
    }

    // Excluído na agenda, o evento continua na tabela (marcado) até sincronizar com o Google
    private fun existe(agendaId: Long): Boolean =
        appContext.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events._ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(agendaId.toString()),
            null
        )?.use { it.moveToFirst() } ?: false

    private fun agendaParaGravar(): AgendaDoCelular? {
        val colunas = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_TYPE
        )
        val filtro = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}" +
            " AND ${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1"
        val agendas = mutableListOf<AgendaDoCelular>()
        appContext.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, colunas, filtro, null, null)?.use { c ->
            while (c.moveToNext()) {
                agendas.add(AgendaDoCelular(c.getLong(0), c.getInt(1) == 1, c.getString(2) == "com.google"))
            }
        }
        return escolherAgenda(agendas)
    }

    /** Compromissos entre os dois dias (inclusive), agrupados por dia. */
    suspend fun buscar(deDia: Int, ateDia: Int): Map<Int, List<CompromissoAgenda>> = withContext(Dispatchers.IO) {
        if (!temPermissao()) return@withContext emptyMap()

        // A tabela Instances já expande eventos recorrentes no intervalo pedido
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, momentoEm(deDia, "00:00"))
            ContentUris.appendId(it, momentoEm(ateDia + 1, "00:00"))
        }.build()

        val colunas = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.CALENDAR_ACCESS_LEVEL
        )

        val porDia = mutableMapOf<Int, MutableList<CompromissoAgenda>>()
        appContext.contentResolver.query(
            uri, colunas, null, null, "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val compromisso = CompromissoAgenda(
                    titulo = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(Sem título)",
                    inicio = c.getLong(1),
                    fim = c.getLong(2),
                    diaInteiro = c.getInt(3) == 1,
                    agenda = c.getString(4).orEmpty(),
                    eventoId = c.getLong(5),
                    local = c.getString(6).orEmpty(),
                    recorrente = !c.getString(7).isNullOrBlank(),
                    editavel = c.getInt(8) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                )
                // Eventos de vários dias aparecem em cada um dos dias
                val primeiro: Int
                val ultimo: Int
                if (compromisso.diaInteiro) {
                    primeiro = diaDoMomentoUtc(compromisso.inicio)
                    ultimo = diaDoMomentoUtc(compromisso.fim - 1)
                } else {
                    primeiro = diaDoMomento(compromisso.inicio)
                    ultimo = diaDoMomento(maxOf(compromisso.inicio, compromisso.fim - 1))
                }
                for (dia in maxOf(primeiro, deDia)..minOf(ultimo, ateDia)) {
                    porDia.getOrPut(dia) { mutableListOf() }.add(compromisso)
                }
            }
        }
        porDia
    }

    private companion object {
        /** Quantos ids vão em cada consulta, abaixo do limite de variáveis do SQLite. */
        const val LOTE_CONSULTA = 400
    }
}

/** Texto da descrição na agenda: o que a pessoa informou e de onde veio. */
fun descricaoDoEvento(evento: Evento): String =
    listOf(evento.detalhes, "Marcado no Folgui").filter { it.isNotBlank() }.joinToString("\n")

// Integração com Google Agenda e Gmail por Intents (o próprio app do Google envia os convites)
object IntegracaoGoogle {

    /** Abre a Google Agenda com o evento preenchido e as pessoas como convidadas (quando ele ainda não está lá). */
    fun adicionarNaAgenda(context: Context, evento: Evento, convidados: List<String>): Boolean {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, evento.titulo)
            putExtra(CalendarContract.Events.EVENT_LOCATION, evento.local)
            putExtra(CalendarContract.Events.DESCRIPTION, "Evento planejado no Folgui em um dia de folga.")
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, evento.momento)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, evento.momento + evento.janela.duracao * 60_000L)
            putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            if (convidados.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, convidados.joinToString(","))
        }
        return abrir(context, intent)
    }

    /** Abre o compromisso na Google Agenda, onde ele pode ser editado ou remarcado. */
    fun abrirCompromisso(context: Context, compromisso: CompromissoAgenda): Boolean {
        val intent = Intent(
            Intent.ACTION_VIEW,
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, compromisso.eventoId)
        ).apply {
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, compromisso.inicio)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, compromisso.fim)
        }
        return abrir(context, intent)
    }

    /** Abre o Gmail (ou outro app de e-mail) com o convite escrito. */
    fun convidarPorEmail(context: Context, evento: Evento, convidados: List<String>): Boolean {
        val texto = buildString {
            appendLine("Oi! Bora para \"${evento.titulo}\"?")
            appendLine()
            appendLine("Quando: ${diaPorExtenso(evento.dia)}, das ${evento.faixaHorario}")
            if (evento.local.isNotBlank()) appendLine("Onde: ${evento.local}")
            appendLine()
            append("Conferi no Folgui e a gente está livre nesse horário.")
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + convidados.joinToString(","))).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Convite: ${evento.titulo} · ${diaResumido(evento.dia)}")
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        return abrir(context, intent)
    }

    private fun abrir(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
