package com.example.folgui

// ============================================================================
// Regras dos widgets, sem Android, para poderem ser testadas.
// O desenho fica em Widgets.kt.
// ============================================================================

private const val ABRIR = "folgui://abrir/"

/** Link sem destino: só abre o app onde ele estiver. */
const val LINK_APP = ABRIR + "app"
const val LINK_AGENDA = ABRIR + "agenda"
const val LINK_EVENTOS = ABRIR + "eventos"
const val LINK_NOVO_EVENTO = ABRIR + "novo"

fun linkDia(dia: Int) = "${ABRIR}dia/$dia"
fun linkEvento(id: Int) = "${ABRIR}evento/$id"

/** Tela do link. A atividade aceita chamadas de fora, então só o que está aqui é aberto. */
fun rotaDoLink(link: String?): String? {
    if (link == null || !link.startsWith(ABRIR)) return null
    val partes = link.removePrefix(ABRIR).split("/")
    val numero = partes.getOrNull(1)?.toIntOrNull()
    return when (partes.first()) {
        "dia" -> numero?.let(Rotas::dia)
        "evento" -> numero?.takeIf { it > 0 }?.let(Rotas::evento)
        "novo" -> Rotas.novoEvento()
        "agenda" -> Rotas.CALENDARIO
        "eventos" -> Rotas.EVENTOS
        else -> null
    }
}

/** Compromisso na lista do widget: do Folgui ou da Google Agenda. */
data class ItemWidget(
    val titulo: String,
    val dia: Int,
    /** null = dia inteiro. */
    val horario: String?,
    val inicio: Long,
    val fim: Long,
    /** Id no Folgui (0 = só existe na agenda). */
    val eventoId: Int = 0,
    val cor: CorGrupo? = null
) {
    val link: String get() = if (eventoId > 0) linkEvento(eventoId) else linkDia(dia)

    /** "Hoje · 14:30", "Amanhã · Dia inteiro", "Sáb, 7 nov · 09:00". */
    fun quando(diaHoje: Int): String {
        val data = when (dia - diaHoje) {
            0 -> "Hoje"
            1 -> "Amanhã"
            else -> diaResumido(dia).capitalizar()
        }
        return "$data · ${horario ?: "Dia inteiro"}"
    }
}

/** O que ainda não terminou, do mais perto para o mais longe, cada compromisso uma vez só. */
fun itensDoWidget(
    eventos: List<Evento>,
    grupos: List<Grupo>,
    agenda: Map<Int, List<CompromissoAgenda>>,
    agora: Long,
    diaHoje: Int
): List<ItemWidget> {
    val cores = grupos.associate { it.id to it.cor }
    val doFolgui = eventos.filterNot { it.foraDaAgenda }.map { e ->
        ItemWidget(
            titulo = e.titulo,
            dia = e.dia,
            horario = e.horario,
            inicio = e.momento,
            fim = e.momento + e.janela.duracao * 60_000L,
            eventoId = e.id,
            cor = cores[e.grupoId]
        )
    }
    // A agenda repete em cada dia o que dura vários dias: fica a ocorrência mais próxima
    val daAgenda = agenda.entries.sortedBy { it.key }
        .filter { it.key >= diaHoje }
        .flatMap { (dia, lista) -> lista.map { dia to it } }
        .distinctBy { (_, c) -> c.eventoId to c.inicio }
        .map { (dia, c) ->
            ItemWidget(
                titulo = c.titulo,
                dia = dia,
                horario = if (c.diaInteiro) null else horarioDoMomento(c.inicio),
                inicio = if (c.diaInteiro) momentoEm(dia, "00:00") else c.inicio,
                fim = if (c.diaInteiro) momentoEm(dia + 1, "00:00") else c.fim
            )
        }
    return (doFolgui + daAgenda).filter { it.fim > agora }.sortedBy { it.inicio }
}

/** Quando o que o widget mostra deixa de valer: a situação da pessoa muda ou um compromisso termina. */
fun proximoDesenho(voce: Pessoa, itens: List<ItemWidget>, agora: Int = minutoAtual()): Long {
    val mudanca = voce.proximaMudanca(agora)
    val momento = momentoEm(Math.floorDiv(mudanca, MINUTOS_DIA), horarioDeMinutos(mudanca))
    return minOf(momento, itens.minOfOrNull { it.fim } ?: momento)
}

private const val CABECALHO_LISTA_DP = 68
private const val LINHA_LISTA_DP = 50
private const val MAX_LINHAS_LISTA = 5

/** Quantos compromissos cabem na altura do widget, sem barra de rolagem. */
fun linhasNaLista(alturaDp: Int): Int =
    ((alturaDp - CABECALHO_LISTA_DP) / LINHA_LISTA_DP).coerceIn(1, MAX_LINHAS_LISTA)
