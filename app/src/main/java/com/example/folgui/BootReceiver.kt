package com.example.folgui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// O Android apaga os alarmes ao reiniciar: os lembretes futuros são agendados novamente
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        FolguiRepository(context).listarEventos().forEach { evento ->
            AlarmScheduler.schedule(context, evento)
        }
    }
}
