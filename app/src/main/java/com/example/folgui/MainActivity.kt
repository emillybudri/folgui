package com.example.folgui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    /** Tela pedida pela notificação ou pelo widget, até o app abri-la. */
    private val rotaPedida = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cria o canal de notificações (Android 8.0+)
        NotificationHelper.createChannel(this)

        enableEdgeToEdge()

        if (savedInstanceState == null) rotaPedida.value = rotaDe(intent)

        val modoFacilSalvo = dataStore.data.map { it[CHAVE_MODO_FACIL] == "1" }

        setContent {
            val modoFacil by modoFacilSalvo.collectAsState(initial = false)
            FolguiTema {
                TamanhoDoTexto(modoFacil) {
                    FolguiApp(rotaPedida = rotaPedida, onRotaAberta = { rotaPedida.value = null })
                }
            }
        }
    }

    // Com o app já aberto, o toque no widget chega aqui em vez de criar outra tela
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        rotaPedida.value = rotaDe(intent)
    }

    // O que mudou no app vai para os widgets uma vez só, ao sair dele
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) WidgetsFolgui.pedirAtualizacao(this)
    }

    private fun rotaDe(intent: Intent): String? =
        intent.getIntExtra(EXTRA_EVENTO, 0).takeIf { it > 0 }?.let(Rotas::evento) ?: rotaDoLink(intent.dataString)

    companion object {
        const val EXTRA_EVENTO = "evento_id"
    }
}
