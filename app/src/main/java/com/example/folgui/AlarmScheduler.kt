package com.example.folgui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

// Agendamento de Tarefas com AlarmManager e PendingIntent
object AlarmScheduler {

    /** Códigos dos alarmes de um evento: o primeiro é o id dele, como sempre foi. */
    private fun codigo(id: Int, indice: Int) = id + indice * 10_000_000

    /**
     * Agenda os lembretes do evento. Retorna false quando não há lembrete a agendar.
     * Quando a agenda já vai avisar, o alarme daqui fica de fora para não avisar duas vezes.
     */
    fun schedule(context: Context, evento: Evento): Boolean {
        cancel(context, evento.id)

        val lembretes = evento.proximosLembretes
        if (evento.foraDaAgenda || lembretes.isEmpty()) return false
        if (evento.avisoNaAgenda) return true
        lembretes.forEachIndexed { i, momento -> agendar(context, evento, momento, i) }
        return true
    }

    private fun agendar(context: Context, evento: Evento, momento: Long, indice: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", evento.id)
            putExtra("titulo", evento.titulo)
            putExtra("local", evento.local)
            putExtra("momento", evento.momento)
        }

        val pending = PendingIntent.getBroadcast(
            context,
            codigo(evento.id, indice),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val podeExato = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        try {
            if (podeExato) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pending)
            }
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pending)
        }
    }

    fun cancel(context: Context, id: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        repeat(MAX_AVISOS) { indice ->
            val pending = PendingIntent.getBroadcast(
                context,
                codigo(id, indice),
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.cancel(pending)
        }
    }
}
