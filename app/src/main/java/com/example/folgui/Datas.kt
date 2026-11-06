package com.example.folgui

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Datas representadas como "número do dia" (dias desde 01/01/1970, sem fuso horário).
// Assim o cálculo das escalas é uma simples conta de diferença entre dias.

private val utc: TimeZone = TimeZone.getTimeZone("UTC")
private const val MS_DIA = 86_400_000L

private val NOMES_MESES = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
)
private val MESES_CURTOS = listOf(
    "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"
)
private val DIAS_SEMANA = listOf(
    "domingo", "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira", "sábado"
)
private val DIAS_SEMANA_CURTOS = listOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb")

/** Iniciais dos dias da semana, começando no domingo. */
val INICIAIS_SEMANA = listOf("D", "S", "T", "Q", "Q", "S", "S")

data class PartesData(val ano: Int, val mes: Int, val dia: Int, val diaSemana: Int)

fun diaDe(ano: Int, mes: Int, diaMes: Int): Int {
    val cal = Calendar.getInstance(utc).apply {
        clear()
        set(ano, mes - 1, diaMes)
    }
    return Math.floorDiv(cal.timeInMillis, MS_DIA).toInt()
}

fun hoje(): Int {
    val cal = Calendar.getInstance()
    return diaDe(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

fun partes(dia: Int): PartesData {
    val cal = Calendar.getInstance(utc).apply { timeInMillis = dia * MS_DIA }
    return PartesData(
        ano = cal.get(Calendar.YEAR),
        mes = cal.get(Calendar.MONTH) + 1,
        dia = cal.get(Calendar.DAY_OF_MONTH),
        diaSemana = cal.get(Calendar.DAY_OF_WEEK) - 1
    )
}

fun diasNoMes(ano: Int, mes: Int): Int {
    val cal = Calendar.getInstance(utc).apply {
        clear()
        set(ano, mes - 1, 1)
    }
    return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
}

/** Converte "dd/MM/aaaa" em número do dia (null se a data não existir). */
fun textoParaDia(texto: String): Int? {
    val p = texto.trim().split("/")
    if (p.size != 3) return null
    val d = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val a = p[2].toIntOrNull() ?: return null
    if (a !in 1900..2200 || m !in 1..12 || d !in 1..diasNoMes(a, m)) return null
    return diaDe(a, m, d)
}

fun diaParaTexto(dia: Int): String {
    val p = partes(dia)
    return String.format(Locale.ROOT, "%02d/%02d/%04d", p.dia, p.mes, p.ano)
}

/** "2026-11-07", formato usado pelas APIs. */
fun diaParaIso(dia: Int): String {
    val p = partes(dia)
    return String.format(Locale.ROOT, "%04d-%02d-%02d", p.ano, p.mes, p.dia)
}

fun isoParaDia(iso: String): Int? {
    val p = iso.split("-")
    if (p.size != 3) return null
    return textoParaDia("${p[2]}/${p[1]}/${p[0]}")
}

fun String.capitalizar(): String = replaceFirstChar { it.uppercase() }

fun nomeMes(mes: Int): String = NOMES_MESES[mes - 1]

fun nomeDiaSemana(dia: Int): String = DIAS_SEMANA[partes(dia).diaSemana]

fun diaSemanaCurto(dia: Int): String = DIAS_SEMANA_CURTOS[partes(dia).diaSemana]

fun mesCurto(dia: Int): String = MESES_CURTOS[partes(dia).mes - 1]

/** "Sábado, 7 de novembro" (o ano aparece apenas quando não é o ano atual). */
fun diaPorExtenso(dia: Int, comDiaSemana: Boolean = true): String {
    val p = partes(dia)
    val anoAtual = partes(hoje()).ano
    val base = "${p.dia} de ${NOMES_MESES[p.mes - 1]}" + if (p.ano != anoAtual) " de ${p.ano}" else ""
    return if (comDiaSemana) "${DIAS_SEMANA[p.diaSemana].capitalizar()}, $base" else base
}

/** "sáb., 7 nov." para listas compactas. */
fun diaResumido(dia: Int): String {
    val p = partes(dia)
    return "${DIAS_SEMANA_CURTOS[p.diaSemana]}, ${p.dia} ${MESES_CURTOS[p.mes - 1]}"
}

/** "Hoje", "Amanhã", "Em 5 dias", "Há 3 dias". */
fun rotuloRelativo(dia: Int): String {
    val diferenca = dia - hoje()
    return when {
        diferenca == 0 -> "Hoje"
        diferenca == 1 -> "Amanhã"
        diferenca == -1 -> "Ontem"
        diferenca > 1 -> "Em $diferenca dias"
        else -> "Há ${-diferenca} dias"
    }
}

fun horarioValido(texto: String): Boolean {
    val p = texto.split(":")
    if (p.size != 2) return false
    val h = p[0].toIntOrNull() ?: return false
    val m = p[1].toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59
}

fun horarioDe(hora: Int, minuto: Int): String = String.format(Locale.ROOT, "%02d:%02d", hora, minuto)

/** Momento (no fuso do celular) correspondente ao dia e horário informados. */
fun momentoEm(dia: Int, horario: String): Long {
    val p = partes(dia)
    val hm = horario.split(":")
    return Calendar.getInstance().apply {
        clear()
        set(p.ano, p.mes - 1, p.dia, hm[0].toInt(), hm[1].toInt(), 0)
    }.timeInMillis
}

/** Dia de um momento em UTC (eventos de dia inteiro da agenda são gravados assim). */
fun diaDoMomentoUtc(millis: Long): Int = Math.floorDiv(millis, MS_DIA).toInt()

fun diaDoMomento(millis: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return diaDe(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

fun horarioDoMomento(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return horarioDe(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

/** Dia da semana sem consultar o calendário (01/01/1970 foi uma quinta-feira). Domingo = 0. */
fun diaDaSemana(dia: Int): Int = Math.floorMod(dia + 4, 7)

fun nomeCurtoDiaSemana(diaSemana: Int): String = DIAS_SEMANA_CURTOS[diaSemana]

/** "6h", "18h30" (minutos desde a meia-noite; passa de 24h no dia seguinte). */
fun horaCurta(minutos: Int): String {
    val m = Math.floorMod(minutos, 1440)
    val hora = m / 60
    val minuto = m % 60
    return if (minuto == 0) "${hora}h" else String.format(Locale.ROOT, "%dh%02d", hora, minuto)
}

/** "das 18h às 23h", "das 18h à meia-noite". */
fun faixaHoras(inicio: Int, fim: Int): String {
    val de = if (Math.floorMod(inicio, 1440) == 0) "da meia-noite" else "das ${horaCurta(inicio)}"
    val ate = if (Math.floorMod(fim, 1440) == 0) "à meia-noite" else "às ${horaCurta(fim)}"
    return "$de $ate"
}

/** "HH:mm" em minutos desde a meia-noite. */
fun minutosDoHorario(horario: String): Int {
    val p = horario.split(":")
    return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
}

/** Minutos desde a meia-noite em "HH:mm". */
fun horarioDeMinutos(minutos: Int): String {
    val m = Math.floorMod(minutos, 1440)
    return horarioDe(m / 60, m % 60)
}

/** Agora, em minutos contados desde 01/01/1970 no fuso do celular (mesma base dos turnos). */
fun minutoAtual(): Int {
    val cal = Calendar.getInstance()
    return hoje() * 1440 + cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

/** "1 dia", "5 dias". */
fun plural(quantidade: Int, singular: String, plural: String = singular + "s"): String =
    "$quantidade ${if (quantidade == 1) singular else plural}"

/** Minuto do dia agora (0 a 1439). */
fun minutoDoDiaAtual(): Int = Math.floorMod(minutoAtual(), 1440)
