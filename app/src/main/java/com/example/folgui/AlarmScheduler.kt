package com.example.folgui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

// Agendamento de Tarefas com AlarmManager e PendingIntent
object AlarmScheduler {

    /** Agenda o lembrete do evento. Retorna false quando não há lembrete a agendar. */
    fun schedule(context: Context, evento: Evento): Boolean {
        cancel(context, evento.id)

        val momento = evento.momentoLembrete ?: return false
        if (momento <= System.currentTimeMillis()) return false

        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", evento.id)
            putExtra("titulo", evento.titulo)
            putExtra("local", evento.local)
            putExtra("momento", evento.momento)
        }

        val pending = PendingIntent.getBroadcast(
            context,
            evento.id,
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
        return true
    }

    fun cancel(context: Context, id: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pending = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pending)
    }
}
