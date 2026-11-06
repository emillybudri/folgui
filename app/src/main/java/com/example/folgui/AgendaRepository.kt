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
    val eventoId: Long = 0
) {
    val horario: String
        get() = if (diaInteiro) "Dia inteiro" else "${horarioDoMomento(inicio)} – ${horarioDoMomento(fim)}"
}

val PERMISSOES_AGENDA = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

/** Uma agenda do celular em que dá para escrever. */
data class AgendaDoCelular(val id: Long, val principal: Boolean, val google: Boolean)

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
     * Retorna o id na agenda, ou null se não deu para gravar.
     */
    fun gravar(evento: Evento, agendaId: Long): Long? {
        if (!temEscrita()) return null
        val valores = ContentValues().apply {
            put(CalendarContract.Events.TITLE, evento.titulo)
            put(CalendarContract.Events.EVENT_LOCATION, evento.local)
            put(CalendarContract.Events.DESCRIPTION, "Marcado no Folgui")
            put(CalendarContract.Events.DTSTART, evento.momento)
            put(CalendarContract.Events.DTEND, evento.momento + evento.duracaoMin * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }
        return try {
            if (agendaId > 0 && existe(agendaId)) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, agendaId)
                appContext.contentResolver.update(uri, valores, null, null)
                agendaId
            } else {
                // Sem vínculo, ou o evento foi apagado na própria agenda: cria de novo
                val agenda = agendaParaGravar() ?: return null
                valores.put(CalendarContract.Events.CALENDAR_ID, agenda.id)
                appContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, valores)
                    ?.let(ContentUris::parseId)
            }
        } catch (e: RuntimeException) {
            null // Cada fabricante tem seu provedor de agenda: uma falha lá não pode derrubar o app
        }
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
            CalendarContract.Instances.EVENT_ID
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
                    eventoId = c.getLong(5)
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
}

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
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, evento.momento + evento.duracaoMin * 60_000L)
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
