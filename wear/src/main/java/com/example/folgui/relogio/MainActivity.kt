package com.example.folgui.relogio

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * O relógio só escuta e mostra. A escala, a IA e os eventos ficam no celular,
 * então a chave do Gemini nunca passa por aqui.
 */
class MainActivity : ComponentActivity() {

    private var tela by mutableStateOf<Tela>(Tela.Inicio)
    private var voz: TextToSpeech? = null

    private val aoReceber = MessageClient.OnMessageReceivedListener { mensagem ->
        if (mensagem.path == CAMINHO_RESPOSTA) mostrar(telaDaResposta(String(mensagem.data)))
    }

    private val ouvir = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        val falado = resultado.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        // Sem texto, a pessoa desistiu ou não falou nada: a tela continua onde estava
        if (!falado.isNullOrBlank()) {
            tela = Tela.Pensando
            enviar(CAMINHO_PEDIDO, falado)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Wearable.getMessageClient(this).addListener(aoReceber)

        // Nem todo relógio tem voz instalada. Sem ela, o resumo continua aparecendo na tela
        voz = TextToSpeech(this) { situacao ->
            if (situacao == TextToSpeech.SUCCESS) voz?.language = Locale("pt", "BR")
        }

        setContent {
            TelasRelogio(
                tela = tela,
                onFalar = ::abrirMicrofone,
                onConfirmar = {
                    tela = Tela.Pensando
                    enviar(CAMINHO_CONFIRMAR, "")
                },
                onCancelar = {
                    enviar(CAMINHO_CANCELAR, "")
                    tela = Tela.Inicio
                },
                onTempoEsgotado = {
                    tela = Tela.Erro("O celular demorou demais para responder. Tente de novo.")
                },
                onFechar = ::finish
            )
        }
    }

    override fun onDestroy() {
        Wearable.getMessageClient(this).removeListener(aoReceber)
        voz?.shutdown()
        super.onDestroy()
    }

    private fun abrirMicrofone() {
        val pedido = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "O que eu marco?")
        try {
            ouvir.launch(pedido)
        } catch (e: ActivityNotFoundException) {
            tela = Tela.Erro("Este relógio não tem reconhecimento de voz.")
        }
    }

    /** Manda a mensagem para o celular que tem o Folgui instalado. */
    private fun enviar(caminho: String, texto: String) {
        lifecycleScope.launch {
            try {
                val alcance = Wearable.getCapabilityClient(this@MainActivity)
                    .getCapability(CAPACIDADE_CELULAR, CapabilityClient.FILTER_REACHABLE)
                    .await()
                val celular = alcance.nodes.firstOrNull { it.isNearby } ?: alcance.nodes.firstOrNull()
                if (celular == null) {
                    tela = Tela.Erro("Não achei o celular. Veja se ele está por perto e com o Folgui instalado.")
                    return@launch
                }
                Wearable.getMessageClient(this@MainActivity)
                    .sendMessage(celular.id, caminho, texto.toByteArray())
                    .await()
            } catch (e: Exception) {
                tela = Tela.Erro("Não consegui falar com o celular. Tente de novo.")
            }
        }
    }

    private fun mostrar(nova: Tela) {
        tela = nova
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        falaDaTela(nova)?.let { voz?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "folgui") }
    }
}
