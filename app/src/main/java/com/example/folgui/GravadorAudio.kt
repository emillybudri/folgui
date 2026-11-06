package com.example.folgui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Tempo máximo de um áudio para a IA, em segundos. */
const val MAX_SEGUNDOS_AUDIO = 60

/** Grava a voz em AAC num arquivo temporário, que é apagado depois do envio. */
class GravadorAudio(private val context: Context) {

    private var gravador: MediaRecorder? = null
    private var arquivo: File? = null

    /** Retorna false se o microfone não pôde ser usado. */
    fun iniciar(): Boolean {
        cancelar()
        val destino = File(context.cacheDir, "pedido_ia.aac")
        val novo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            novo.setAudioSource(MediaRecorder.AudioSource.MIC)
            novo.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            novo.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            novo.setAudioSamplingRate(16_000)
            novo.setAudioChannels(1)
            novo.setAudioEncodingBitRate(32_000)
            novo.setOutputFile(destino.absolutePath)
            novo.prepare()
            novo.start()
            gravador = novo
            arquivo = destino
            true
        } catch (e: Exception) {
            novo.release()
            destino.delete()
            false
        }
    }

    /** Termina a gravação e devolve o arquivo (null se ficou curta demais para virar áudio). */
    fun parar(): File? {
        val atual = gravador ?: return null
        val destino = arquivo
        gravador = null
        arquivo = null
        return try {
            atual.stop()
            destino?.takeIf { it.length() > 0 }
        } catch (e: RuntimeException) {
            // O MediaRecorder falha no stop() quando quase nada foi gravado
            destino?.delete()
            null
        } finally {
            atual.release()
        }
    }

    fun cancelar() {
        parar()?.delete()
    }
}
