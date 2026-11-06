package com.example.folgui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// BroadcastReceiver acionado pelo AlarmManager no horário do lembrete
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val titulo = intent.getStringExtra("titulo") ?: "Evento na folga"
        val local = intent.getStringExtra("local").orEmpty()
        val momento = intent.getLongExtra("momento", System.currentTimeMillis())

        val dia = diaDoMomento(momento)
        val quando = when (dia - hoje()) {
            0 -> "Hoje"
            1 -> "Amanhã"
            else -> diaPorExtenso(dia)
        }
        val texto = buildString {
            append("$quando às ${horarioDoMomento(momento)}")
            if (local.isNotBlank()) append(" · $local")
        }
        NotificationHelper.show(context, id, titulo, texto)
    }
}
