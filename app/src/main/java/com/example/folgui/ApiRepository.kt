package com.example.folgui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Feriado(val dia: Int, val nome: String)

data class Previsao(
    val cidade: String,
    val regiao: String,
    val maxima: Double,
    val minima: Double,
    val chanceChuva: Int?,
    val codigoTempo: Int
)

/** A Open-Meteo fornece previsão diária para hoje e os próximos 15 dias. */
const val DIAS_PREVISAO = 15

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

    // Previsão do tempo da cidade no dia do evento (Open-Meteo). Retorna null se a cidade não existir.
    suspend fun buscarPrevisao(nomeCidade: String, dia: Int): Previsao? = withContext(Dispatchers.IO) {
        val nome = URLEncoder.encode(nomeCidade.trim(), "UTF-8")
        val geo = JSONObject(
            fazerGet("https://geocoding-api.open-meteo.com/v1/search?name=$nome&count=1&language=pt")
        )
        val resultados = geo.optJSONArray("results")
        if (resultados == null || resultados.length() == 0) return@withContext null

        val local = resultados.getJSONObject(0)
        val cidade = local.getString("name")
        val regiao = listOf(local.optString("admin1"), local.optString("country"))
            .filter { it.isNotBlank() && it != cidade }
            .joinToString(", ")

        val data = diaParaIso(dia)
        val clima = JSONObject(
            fazerGet(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${local.getDouble("latitude")}&longitude=${local.getDouble("longitude")}" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&timezone=auto&start_date=$data&end_date=$data"
            )
        )
        val diario = clima.getJSONObject("daily")
        val chuva = diario.getJSONArray("precipitation_probability_max")

        Previsao(
            cidade = cidade,
            regiao = regiao,
            maxima = diario.getJSONArray("temperature_2m_max").getDouble(0),
            minima = diario.getJSONArray("temperature_2m_min").getDouble(0),
            chanceChuva = if (chuva.isNull(0)) null else chuva.getInt(0),
            codigoTempo = diario.getJSONArray("weather_code").getInt(0)
        )
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

fun descricaoTempo(codigo: Int): String = when (codigo) {
    0 -> "Céu limpo"
    1 -> "Predomínio de sol"
    2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Neblina"
    in 51..57 -> "Garoa"
    in 61..67 -> "Chuva"
    in 71..77 -> "Neve"
    in 80..82 -> "Pancadas de chuva"
    in 95..99 -> "Tempestade"
    else -> "Tempo variável"
}
