package com.example.folgui

import java.text.Normalizer

// ============================================================================
// Grupos de compromissos: são do Folgui, porque a Google Agenda não tem pastas.
// A ligação com a agenda é o id de cada evento; a cor é do grupo e os eventos a herdam.
// Regras sem Android, para poderem ser testadas.
// ============================================================================

/**
 * As 11 cores de evento da Google Agenda. Cor fora desta lista não sincroniza, então a paleta
 * é fechada. [id] é o colorId do Google e [comum] é como as pessoas falam.
 */
enum class CorGrupo(val id: Int, val nome: String, val comum: String, val argb: Long, val apelidos: List<String>) {
    LAVANDA(1, "Lavanda", "lilás", 0xFF7986CB, listOf("lilas", "lavanda")),
    SALVIA(2, "Sálvia", "verde-clara", 0xFF33B679, listOf("verde claro", "verde clara", "salvia")),
    UVA(3, "Uva", "roxa", 0xFF8E24AA, listOf("roxo", "roxa", "uva", "violeta", "purpura")),
    FLAMINGO(4, "Flamingo", "rosa", 0xFFE67C73, listOf("rosa", "flamingo", "salmao")),
    BANANA(5, "Banana", "amarela", 0xFFF6BF26, listOf("amarelo", "amarela", "banana")),
    TANGERINA(6, "Tangerina", "laranja", 0xFFF4511E, listOf("laranja", "tangerina")),
    PAVAO(7, "Pavão", "azul", 0xFF039BE5, listOf("azul", "azul claro", "azul clara", "pavao", "ciano")),
    GRAFITE(8, "Grafite", "cinza", 0xFF616161, listOf("cinza", "grafite")),
    MIRTILO(9, "Mirtilo", "azul-escura", 0xFF3F51B5, listOf("azul escuro", "azul escura", "mirtilo", "anil")),
    MANJERICAO(10, "Manjericão", "verde", 0xFF0B8043, listOf("verde", "verde escuro", "verde escura", "manjericao")),
    TOMATE(11, "Tomate", "vermelha", 0xFFD50000, listOf("vermelho", "vermelha", "tomate"));

    companion object {
        fun porId(id: Int): CorGrupo? = entries.firstOrNull { it.id == id }

        fun porNome(texto: String): CorGrupo? {
            val procurado = normalizarTexto(texto)
            return entries.firstOrNull { c -> procurado == normalizarTexto(c.nome) || procurado in c.apelidos }
        }
    }
}

data class Grupo(val id: Int = 0, val nome: String, val cor: CorGrupo)

/** Uma cor de evento que a conta oferece no aparelho (tabela Colors do provedor de calendário). */
data class CorDaConta(val chave: String, val argb: Int)

/** Sem acento, maiúscula ou espaço sobrando: "Consultas " e "consultas" são o mesmo grupo. */
fun normalizarTexto(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .trim()
        .replace(Regex("\\s+"), " ")

fun grupoPorNome(grupos: List<Grupo>, nome: String): Grupo? {
    val procurado = normalizarTexto(nome)
    return grupos.firstOrNull { normalizarTexto(it.nome) == procurado }
}

/** Mensagem de erro do nome, ou null se puder ser usado. */
fun erroNomeGrupo(nome: String, grupos: List<Grupo>, ignorarId: Int = 0): String? = when {
    nome.isBlank() -> "Dê um nome"
    grupos.any { it.id != ignorarId && normalizarTexto(it.nome) == normalizarTexto(nome) } -> "Já existe esse grupo"
    else -> null
}

/** A primeira cor que nenhum grupo usa, para os grupos ficarem diferentes entre si. */
fun corSugerida(grupos: List<Grupo>): CorGrupo =
    CorGrupo.entries.firstOrNull { c -> grupos.none { it.cor == c } } ?: CorGrupo.PAVAO

fun eventosDoGrupo(eventos: List<Evento>, grupoId: Int): List<Evento> =
    if (grupoId <= 0) emptyList() else eventos.filter { it.grupoId == grupoId }

/** Grupo do evento; se ele foi apagado em outro aparelho, o evento fica como sem grupo. */
fun grupoDoEvento(evento: Evento, grupos: List<Grupo>): Grupo? = grupos.firstOrNull { it.id == evento.grupoId }

/**
 * Qual chave de cor gravar na conta. O Google usa o colorId como chave; outra conta pode usar
 * outras chaves, e aí vale a cor mais parecida. Sem nenhuma cor na conta, retorna null.
 */
fun chaveDaCor(cor: CorGrupo, disponiveis: List<CorDaConta>): String? {
    if (disponiveis.isEmpty()) return null
    disponiveis.firstOrNull { it.chave == cor.id.toString() }?.let { return it.chave }
    fun canal(argb: Long, deslocamento: Int) = ((argb shr deslocamento) and 0xFF).toInt()
    fun distancia(c: CorDaConta): Int {
        val a = c.argb.toLong()
        return listOf(16, 8, 0).sumOf { s -> (canal(a, s) - canal(cor.argb, s)).let { it * it } }
    }
    return disponiveis.minByOrNull(::distancia)?.chave
}

// ----------------------------------------------------------------------------
// Conciliação: a Google Agenda pode mudar sem o Folgui saber
// ----------------------------------------------------------------------------

/** Como um evento está agora na agenda do aparelho. */
data class EventoNaAgenda(
    val id: Long,
    val titulo: String,
    val inicio: Long,
    val fim: Long,
    val local: String,
    val corChave: String?,
    /** Lembretes em minutos, do maior para o menor. */
    val avisos: List<Int>,
    /** Chave de cada cor na conta deste evento. */
    val chaves: Map<CorGrupo, String> = emptyMap()
)

data class Conciliacao(
    /** Eventos do Folgui já com o que foi mudado direto na agenda. */
    val atualizados: List<Evento> = emptyList(),
    /** Sumiram da agenda. Não são apagados aqui: pode ser só a conta que saiu do aparelho. */
    val apagados: List<Evento> = emptyList(),
    /** Voltaram a existir na agenda. */
    val reapareceram: List<Evento> = emptyList(),
    /** Saíram da cor do grupo: a cor é do grupo, então ela é aplicada de novo. */
    val recolorir: List<Evento> = emptyList()
)

/** Compara os eventos do Folgui com a agenda. A agenda vence, porque o Folgui grava nela na hora. */
fun conciliarAgenda(eventos: List<Evento>, lidos: Map<Long, EventoNaAgenda>, grupos: List<Grupo>): Conciliacao {
    val atualizados = mutableListOf<Evento>()
    val apagados = mutableListOf<Evento>()
    val reapareceram = mutableListOf<Evento>()
    val recolorir = mutableListOf<Evento>()

    eventos.filter { it.agendaId > 0 }.forEach { e ->
        val lido = lidos[e.agendaId]
        if (lido == null) {
            if (!e.foraDaAgenda) apagados.add(e)
            return@forEach
        }
        if (e.foraDaAgenda) reapareceram.add(e)

        val novo = eventoComAgenda(e, lido)
        if (novo != e) atualizados.add(novo)

        val esperada = grupoDoEvento(e, grupos)?.let { lido.chaves[it.cor] }
        if (esperada != null && esperada != lido.corChave) recolorir.add(e)
    }
    return Conciliacao(atualizados, apagados, reapareceram, recolorir)
}

private fun eventoComAgenda(e: Evento, lido: EventoNaAgenda): Evento {
    val minutos = ((lido.fim - lido.inicio) / 60_000L).toInt().coerceIn(1, MINUTOS_DIA)
    val mesmaHora = lido.inicio == e.momento
    val atualizado = e.copy(
        titulo = lido.titulo.ifBlank { e.titulo },
        local = lido.local,
        dia = if (mesmaHora) e.dia else diaDoMomento(lido.inicio),
        horario = if (mesmaHora) e.horario else horarioDoMomento(lido.inicio),
        // Sem fim no Folgui, o evento vai para a agenda com a hora reservada: isso não é uma mudança
        duracaoMin = if (minutos == duracaoReservada(e.duracaoMin)) e.duracaoMin else minutos
    )
    // Evento antigo não tem lembrete na agenda: o aviso dele continua sendo o do Folgui
    return if (e.avisoNaAgenda && lido.avisos != e.avisos) atualizado.comAvisos(lido.avisos) else atualizado
}
