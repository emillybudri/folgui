package com.example.folgui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cria o canal de notificações (Android 8.0+)
        NotificationHelper.createChannel(this)

        enableEdgeToEdge()

        // Evento a abrir quando o app é aberto pela notificação
        val eventoNotificacao = if (savedInstanceState == null) intent.getIntExtra(EXTRA_EVENTO, 0) else 0

        val modoFacilSalvo = dataStore.data.map { it[CHAVE_MODO_FACIL] == "1" }

        setContent {
            val modoFacil by modoFacilSalvo.collectAsState(initial = false)
            FolguiTema {
                TamanhoDoTexto(modoFacil) {
                    FolguiApp(eventoInicial = eventoNotificacao)
                }
            }
        }
    }

    companion object {
        const val EXTRA_EVENTO = "evento_id"
    }
}
