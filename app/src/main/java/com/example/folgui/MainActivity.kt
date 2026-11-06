package com.example.folgui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cria o canal de notificações (Android 8.0+)
        NotificationHelper.createChannel(this)

        enableEdgeToEdge()

        // Evento a abrir quando o app é aberto pela notificação
        val eventoNotificacao = if (savedInstanceState == null) intent.getIntExtra(EXTRA_EVENTO, 0) else 0

        setContent {
            FolguiTema {
                FolguiApp(eventoInicial = eventoNotificacao)
            }
        }
    }

    companion object {
        const val EXTRA_EVENTO = "evento_id"
    }
}
