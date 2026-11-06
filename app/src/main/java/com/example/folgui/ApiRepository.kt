package com.example.folgui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class Feriado(val dia: Int, val nome: String)

// Consumo de APIs REST com HttpURLConnection e JSONObject/JSONArray em Dispatchers.IO
class ApiRepository {

    // Feriados nacionais do ano na BrasilAPI
    suspend fun buscarFeriados(ano: Int): List<Feriado> = withContext(Dispatchers.IO) {
        val json = JSONArray(fazerGet("https://brasilapi.com.br/api/feriados/v1/$ano"))
        val feriados = mutableListOf<Feriado>()
        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            val dia = isoParaDia(item.getString("date")) ?: continue
            feriados.add(Feriado(dia, item.getString("name")))
        }
        feriados
    }

    private fun fazerGet(endereco: String): String {
        val conexao = URL(endereco).openConnection() as HttpURLConnection
        try {
            conexao.requestMethod = "GET"
            conexao.connectTimeout = 10000
            conexao.readTimeout = 10000

            if (conexao.responseCode !in 200..299) {
                throw IllegalStateException("Erro HTTP ${conexao.responseCode}")
            }
            return conexao.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexao.disconnect()
        }
    }
}
