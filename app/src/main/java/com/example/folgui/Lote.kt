package com.example.folgui

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

// ============================================================================
// Vários compromissos de uma vez. A IA só lê o pedido e devolve ajustes (AjusteLote);
// quem guarda o plano, decide o que falta perguntar e confere conflitos e repetições é o app.
// Regras sem Android, para poderem ser testadas.
// ============================================================================

/** Duração usada quando a pessoa não informa: aparece na prévia e pode ser trocada. */
const val DURACAO_PADRAO_LOTE = 60

val OPCOES_DURACAO_LOTE = listOf(30, 45, 60, 90, 120)

enum class EstadoItem { PENDENTE, CRIADO, FALHOU }

data class ItemLote(
    /** Número na lista: é assim que a IA entende "só nos três primeiros". */
    val n: Int,
    val titulo: String,
    val profissional: String = "",
    val categoria: String = "",
    val dia: Int,
    val horario: String,
    val local: String = "",
    /** Null = usa a duração padrão do plano. */
    val duracaoMin: Int? = null,
    /** Null = usa os avisos do plano. */
    val avisos: List<Int>? = null,
    /** A pessoa mandou criar mesmo já existindo um igual. */
    val permitirRepetido: Boolean = false,
    val estado: EstadoItem = EstadoItem.PENDENTE,
    val erro: String = ""
)

data class PlanoLote(
    val itens: List<ItemLote>,
    /** Nome que a IA sugeriu, antes de a pessoa responder. */
    val grupoSugerido: String = "",
    val grupoDecidido: Boolean = false,
    /** Vazio com [grupoDecidido] = sem grupo: os eventos ficam com a cor padrão da agenda. */
    val grupoNome: String = "",
    val cor: CorGrupo? = null,
    val corDecidida: Boolean = false,
    /** Null = ainda não decidido; vazio = sem avisos. */
    val avisos: List<Int>? = null,
    val duracaoPadrao: Int = DURACAO_PADRAO_LOTE
) {
    val pendentes: List<ItemLote> get() = itens.filter { it.estado != EstadoItem.CRIADO }
    val concluido: Boolean get() = itens.isNotEmpty() && pendentes.isEmpty()
    val temGrupo: Boolean get() = grupoDecidido && grupoNome.isNotBlank()
}

/** Um compromisso lido pela IA. */
data class NovoItem(
    val titulo: String,
    val profissional: String = "",
    val categoria: String = "",
    val dia: Int,
    val horario: String,
    val local: String = "",
    val duracaoMin: Int? = null
)

/** Mudança pedida em um item já na lista; campo nulo fica como está. */
data class EdicaoItem(
    val n: Int,
    val titulo: String? = null,
    val dia: Int? = null,
    val horario: String? = null,
    val local: String? = null
)

/** O que a IA entendeu da fala da pessoa sobre o plano. */
data class AjusteLote(
    val novos: List<NovoItem> = emptyList(),
    /** Compromissos sem data ou hora, que não dá para marcar. */
    val incompletos: Int = 0,
    val grupoSugerido: String = "",
    val grupo: String = "",
    val semGrupo: Boolean = false,
    val cor: CorGrupo? = null,
    val manterCor: Boolean = false,
    val avisos: List<Int>? = null,
    val semAvisos: Boolean = false,
    val itensAvisos: List<Int> = emptyList(),
    val duracaoMin: Int? = null,
    val itensDuracao: List<Int> = emptyList(),
    val remover: List<Int> = emptyList(),
    val manter: List<Int> = emptyList(),
    val editar: List<EdicaoItem> = emptyList(),
    val confirmar: Boolean = false,
    val cancelar: Boolean = false
) {
    /** A fala mudou algo no plano, além de confirmar ou cancelar. */
    val mudaPlano: Boolean
        get() = this != AjusteLote(confirmar = confirmar, cancelar = cancelar)
}

private fun chaveItem(titulo: String, dia: Int, horario: String) = "${normalizarTexto(titulo)}|$dia|$horario"

/** Junta os compromissos novos ao plano, sem repetir, em ordem de data. */
private fun juntarNovos(plano: PlanoLote, novos: List<NovoItem>): PlanoLote {
    val vistos = plano.itens.map { chaveItem(it.titulo, it.dia, it.horario) }.toMutableSet()
    val unicos = novos.filter { vistos.add(chaveItem(it.titulo, it.dia, it.horario)) }
        .sortedBy { it.dia * MINUTOS_DIA + minutosDoHorario(it.horario) }
    val inicio = (plano.itens.maxOfOrNull { it.n } ?: 0) + 1
    return plano.copy(
        itens = plano.itens + unicos.mapIndexed { i, novo ->
            ItemLote(
                n = inicio + i,
                titulo = novo.titulo,
                profissional = novo.profissional,
                categoria = novo.categoria,
                dia = novo.dia,
                horario = novo.horario,
                local = novo.local,
                duracaoMin = novo.duracaoMin
            )
        }
    )
}

/** Escolhe o grupo: um que já existe traz a cor dele, e um novo ainda precisa de cor. */
fun escolherGrupo(plano: PlanoLote, nome: String, grupos: List<Grupo>): PlanoLote {
    val existente = grupoPorNome(grupos, nome)
    return plano.copy(
        grupoDecidido = true,
        grupoNome = existente?.nome ?: nome.trim().take(40),
        cor = existente?.cor ?: plano.cor,
        corDecidida = false
    )
}

fun semGrupo(plano: PlanoLote): PlanoLote =
    plano.copy(grupoDecidido = true, grupoNome = "", cor = null, corDecidida = true)

/** Aplica a fala da pessoa. Retorna null quando não sobra nada para criar. */
fun aplicarAjuste(atual: PlanoLote?, ajuste: AjusteLote, grupos: List<Grupo>): PlanoLote? {
    if (ajuste.cancelar) return null
    // Um plano terminado não recebe mais nada: a lista nova começa do zero
    var plano = atual?.takeIf { !it.concluido } ?: PlanoLote(emptyList())
    plano = juntarNovos(plano, ajuste.novos)
    if (ajuste.grupoSugerido.isNotBlank()) plano = plano.copy(grupoSugerido = ajuste.grupoSugerido.trim().take(40))

    when {
        ajuste.semGrupo -> plano = semGrupo(plano)
        ajuste.grupo.isNotBlank() -> plano = escolherGrupo(plano, ajuste.grupo, grupos)
    }
    if (ajuste.cor != null && plano.temGrupo) plano = plano.copy(cor = ajuste.cor, corDecidida = true)
    if (ajuste.manterCor && plano.temGrupo && plano.cor != null) plano = plano.copy(corDecidida = true)

    val avisos = if (ajuste.semAvisos) emptyList() else ajuste.avisos?.let(::normalizarAvisos)
    if (avisos != null) {
        plano = if (ajuste.itensAvisos.isEmpty()) {
            plano.copy(avisos = avisos, itens = plano.itens.map { it.copy(avisos = null) })
        } else {
            // "Só nos três primeiros": os outros ficam sem aviso, a não ser que já tenham decidido
            plano.copy(
                avisos = plano.avisos ?: emptyList(),
                itens = plano.itens.map { if (it.n in ajuste.itensAvisos) it.copy(avisos = avisos) else it }
            )
        }
    }

    ajuste.duracaoMin?.let { minutos ->
        plano = if (ajuste.itensDuracao.isEmpty()) {
            plano.copy(duracaoPadrao = minutos, itens = plano.itens.map { it.copy(duracaoMin = null) })
        } else {
            plano.copy(itens = plano.itens.map { if (it.n in ajuste.itensDuracao) it.copy(duracaoMin = minutos) else it })
        }
    }

    plano = plano.copy(
        itens = plano.itens.map { item ->
            val e = ajuste.editar.firstOrNull { it.n == item.n && item.estado != EstadoItem.CRIADO }
            if (e == null) {
                item
            } else {
                item.copy(
                    titulo = e.titulo ?: item.titulo,
                    dia = e.dia ?: item.dia,
                    horario = e.horario ?: item.horario,
                    local = e.local ?: item.local
                )
            }
        }.filter { item ->
            item.estado == EstadoItem.CRIADO ||
                (item.n !in ajuste.remover && (ajuste.manter.isEmpty() || item.n in ajuste.manter))
        }
    )
    return plano.takeIf { it.itens.isNotEmpty() }
}

fun duracaoDoItem(item: ItemLote, plano: PlanoLote): Int = item.duracaoMin ?: plano.duracaoPadrao

fun avisosDoItem(item: ItemLote, plano: PlanoLote): List<Int> = item.avisos ?: plano.avisos.orEmpty()

/** O evento que o item vai virar. */
fun eventoDoItem(item: ItemLote, plano: PlanoLote, grupoId: Int = 0): Evento = Evento(
    titulo = item.titulo,
    dia = item.dia,
    horario = item.horario,
    local = item.local,
    antecedenciaMin = -1,
    duracaoMin = duracaoDoItem(item, plano),
    participantes = emptySet(),
    grupoId = grupoId,
    detalhes = listOfNotNull(
        item.profissional.takeIf { it.isNotBlank() }?.let { "Profissional: $it" },
        item.categoria.takeIf { it.isNotBlank() }?.let { "Categoria: $it" }
    ).joinToString("\n")
).comAvisos(avisosDoItem(item, plano))

// ----------------------------------------------------------------------------
// Conferência: repetidos e conflitos, sempre contra os dados reais
// ----------------------------------------------------------------------------

data class AnaliseItem(
    /** Já existe igual no Folgui ou na agenda: fica de fora, a não ser que a pessoa peça. */
    val repetido: Boolean = false,
    val conflitos: List<String> = emptyList()
)

/**
 * Confere cada item pendente. [compromissos] é a leitura da agenda no período do plano;
 * os eventos do Folgui que também estão lá são contados uma vez só.
 */
fun analisarPlano(
    plano: PlanoLote,
    eventos: List<Evento>,
    compromissos: List<CompromissoAgenda>,
    voce: Pessoa?
): Map<Int, AnaliseItem> {
    val nossos = eventos.map { it.agendaId }.filter { it > 0 }.toSet()
    val externos = compromissos.filter { it.eventoId !in nossos }.distinctBy { it.eventoId to it.inicio }
    val ativos = plano.pendentes

    return ativos.associate { item ->
        val evento = eventoDoItem(item, plano)
        val titulo = normalizarTexto(item.titulo)
        val fim = evento.momento + evento.janela.duracao * 60_000L

        val repetidoFolgui = eventos.filter {
            normalizarTexto(it.titulo) == titulo && it.dia == item.dia && it.horario == item.horario
        }
        val repetidoAgenda = externos.filter { normalizarTexto(it.titulo) == titulo && it.inicio == evento.momento }

        val conflitos = mutableListOf<String>()
        when (voce?.motivoOcupado(item.dia, evento.janela)) {
            TipoBloco.SONO -> conflitos.add("Você estará dormindo")
            TipoBloco.TRABALHO -> conflitos.add("Você estará trabalhando")
            TipoBloco.DESCANSO -> conflitos.add("Você estará descansando do plantão")
            null -> Unit
        }
        eventosEmConflito(eventos - repetidoFolgui.toSet(), item.dia, evento.janela).forEach {
            conflitos.add("Coincide com \"${it.titulo}\" (${it.faixaHorario})")
        }
        (externos - repetidoAgenda.toSet())
            .filter { !it.diaInteiro && it.inicio < fim && evento.momento < it.fim }
            .forEach { conflitos.add("Coincide com \"${it.titulo}\" na agenda (${it.horario})") }
        ativos.filter { it.n != item.n && it.dia == item.dia }.forEach { outro ->
            val janela = eventoDoItem(outro, plano).janela
            if (janela.inicio < evento.janela.fim && evento.janela.inicio < janela.fim) {
                conflitos.add("Coincide com \"${outro.titulo}\" (${outro.horario})")
            }
        }
        if (evento.jaPassou) conflitos.add("Essa data já passou")

        item.n to AnaliseItem(repetidoFolgui.isNotEmpty() || repetidoAgenda.isNotEmpty(), conflitos)
    }
}

/** Itens que vão ser criados na confirmação. */
fun itensParaCriar(plano: PlanoLote, analises: Map<Int, AnaliseItem>): List<ItemLote> =
    plano.pendentes.filter { analises[it.n]?.repetido != true || it.permitirRepetido }

// ----------------------------------------------------------------------------
// O que falta decidir
// ----------------------------------------------------------------------------

/** Grupo, cor e avisos já definidos: a pessoa só precisa confirmar. */
fun planoPronto(plano: PlanoLote): Boolean =
    plano.grupoDecidido && (!plano.temGrupo || (plano.cor != null && plano.corDecidida)) && plano.avisos != null

private fun compromissos(n: Int) = plural(n, "compromisso")

/** A próxima fala da assistente. Pergunta só o que falta, então um pedido completo vai direto à prévia. */
fun perguntaDoPlano(plano: PlanoLote, grupos: List<Grupo>, novos: Int = 0, analises: Map<Int, AnaliseItem> = emptyMap()): String {
    val partes = mutableListOf<String>()
    if (novos > 0) partes.add("Encontrei ${compromissos(novos)}!")
    val total = plano.pendentes.size
    val repetidos = plano.pendentes.count { analises[it.n]?.repetido == true && !it.permitirRepetido }
    if (repetidos > 0) {
        partes.add(
            if (repetidos == total) "Todos já estão na sua agenda, então não vou criar de novo."
            else "${compromissos(repetidos).capitalizar()} já ${if (repetidos == 1) "está" else "estão"} na sua agenda e ${if (repetidos == 1) "ficou" else "ficaram"} de fora."
        )
    }
    val existente = grupoPorNome(grupos, plano.grupoNome)
    when {
        !plano.grupoDecidido && plano.grupoSugerido.isNotBlank() -> {
            val quantos = if (total > 1) "todos" else "esse"
            partes.add("Quer que eu organize $quantos no grupo ${plano.grupoSugerido}?")
        }
        !plano.grupoDecidido -> partes.add("Quer organizar em um grupo? Diga o nome ou escolha abaixo.")
        plano.temGrupo && !plano.corDecidida && existente != null -> partes.add(
            "Perfeito! O grupo ${existente.nome} já existe e usa a cor ${existente.cor.comum}. Quer manter essa cor?"
        )
        plano.temGrupo && !plano.corDecidida -> partes.add("Qual cor o grupo ${plano.grupoNome} vai ter?")
    }
    if (plano.grupoDecidido && plano.avisos == null) {
        partes.add("E quer receber notificações antes dos compromissos?")
    }
    if (planoPronto(plano)) {
        partes.add("Confira a prévia. Nada é criado até você confirmar.")
    }
    return partes.joinToString(" ")
}

/** Texto que a IA recebe a cada fala, para entender respostas curtas como "sim" ou "muda para azul". */
fun contextoDoPlano(plano: PlanoLote, grupos: List<Grupo>): String = buildString {
    appendLine("PLANO EM ANDAMENTO (ainda não criado):")
    plano.itens.forEach { i ->
        val avisos = i.avisos?.let { ", avisos ${rotuloAvisos(it)}" }.orEmpty()
        appendLine("item ${i.n}: \"${i.titulo}\" ${diaParaIso(i.dia)} ${i.horario}, ${duracaoDoItem(i, plano)} min$avisos" +
            if (i.estado == EstadoItem.CRIADO) " (já criado)" else "")
    }
    appendLine(
        when {
            !plano.grupoDecidido -> "Grupo: não decidido" + plano.grupoSugerido.takeIf { it.isNotBlank() }?.let { " (sugerido: $it)" }.orEmpty()
            plano.temGrupo -> "Grupo: ${plano.grupoNome}, cor ${plano.cor?.comum ?: "não escolhida"}" + if (plano.corDecidida) "" else " (a pessoa ainda não confirmou a cor)"
            else -> "Grupo: nenhum"
        }
    )
    appendLine("Avisos: ${plano.avisos?.let(::rotuloAvisos) ?: "não decidido"}.")
    appendLine("Última pergunta feita à pessoa: ${perguntaDoPlano(plano, grupos)}")
}

/** Resumo depois de criar: quantos deram certo e o que falhou, com o motivo. */
fun resumoDoLote(plano: PlanoLote): String {
    val criados = plano.itens.count { it.estado == EstadoItem.CRIADO }
    val falhas = plano.itens.filter { it.estado == EstadoItem.FALHOU }
    return buildString {
        append(
            when {
                criados == 0 -> "Não consegui criar nenhum evento."
                falhas.isEmpty() -> "Pronto! ${plural(criados, "evento")} na sua agenda"
                else -> "Criei $criados de ${criados + falhas.size} eventos"
            }
        )
        if (criados > 0) {
            if (plano.temGrupo) append(" no grupo ${plano.grupoNome}")
            val avisos = plano.avisos.orEmpty()
            if (avisos.isNotEmpty()) append(", com aviso ${rotuloAvisos(avisos)}")
            append(".")
        }
        falhas.forEach { append("\n${it.titulo}: ${it.erro}.") }
    }
}

// ----------------------------------------------------------------------------
// Criação
// ----------------------------------------------------------------------------

/** Resultado de um evento do lote: [erro] nulo quer dizer que ele foi criado. */
data class ResultadoCriacao(val erro: String? = null)

/**
 * Cria os [itens] um por vez e devolve o plano com a situação de cada um. Quem já foi criado
 * nunca passa por aqui de novo, então repetir a tentativa só mexe nos pendentes.
 */
suspend fun executarLote(
    plano: PlanoLote,
    itens: List<ItemLote>,
    grupoId: Int,
    criar: suspend (Evento) -> ResultadoCriacao,
    onProgresso: (PlanoLote) -> Unit = {}
): PlanoLote {
    var atual = plano
    itens.filter { it.estado != EstadoItem.CRIADO }.forEach { item ->
        val resultado = try {
            criar(eventoDoItem(item, plano, grupoId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoCriacao("Erro inesperado")
        }
        atual = atual.copy(
            itens = atual.itens.map {
                when {
                    it.n != item.n -> it
                    resultado.erro == null -> it.copy(estado = EstadoItem.CRIADO, erro = "")
                    else -> it.copy(estado = EstadoItem.FALHOU, erro = resultado.erro)
                }
            }
        )
        onProgresso(atual)
    }
    return atual
}

// ----------------------------------------------------------------------------
// O plano sobrevive a girar a tela: vai para texto e volta
// ----------------------------------------------------------------------------

private fun inteiros(lista: List<Int>) = JSONArray().apply { lista.forEach { put(it) } }

private fun listaDeInteiros(a: JSONArray) = (0 until a.length()).map(a::getInt)

fun planoParaTexto(plano: PlanoLote): String = JSONObject()
    .put("grupoSugerido", plano.grupoSugerido)
    .put("grupoDecidido", plano.grupoDecidido)
    .put("grupoNome", plano.grupoNome)
    .put("cor", plano.cor?.id ?: 0)
    .put("corDecidida", plano.corDecidida)
    .put("avisos", plano.avisos?.let(::inteiros) ?: JSONObject.NULL)
    .put("duracaoPadrao", plano.duracaoPadrao)
    .put(
        "itens",
        JSONArray().apply {
            plano.itens.forEach { i ->
                put(
                    JSONObject()
                        .put("n", i.n)
                        .put("titulo", i.titulo)
                        .put("profissional", i.profissional)
                        .put("categoria", i.categoria)
                        .put("dia", i.dia)
                        .put("horario", i.horario)
                        .put("local", i.local)
                        .put("duracao", i.duracaoMin ?: 0)
                        .put("avisos", i.avisos?.let(::inteiros) ?: JSONObject.NULL)
                        .put("repetido", i.permitirRepetido)
                        .put("estado", i.estado.name)
                        .put("erro", i.erro)
                )
            }
        }
    )
    .toString()

fun textoParaPlano(texto: String): PlanoLote? = try {
    val o = JSONObject(texto)
    val lista = o.getJSONArray("itens")
    PlanoLote(
        itens = (0 until lista.length()).map { k ->
            val i = lista.getJSONObject(k)
            ItemLote(
                n = i.getInt("n"),
                titulo = i.getString("titulo"),
                profissional = i.getString("profissional"),
                categoria = i.getString("categoria"),
                dia = i.getInt("dia"),
                horario = i.getString("horario"),
                local = i.getString("local"),
                duracaoMin = i.getInt("duracao").takeIf { it > 0 },
                avisos = i.optJSONArray("avisos")?.let(::listaDeInteiros),
                permitirRepetido = i.getBoolean("repetido"),
                estado = EstadoItem.valueOf(i.getString("estado")),
                erro = i.getString("erro")
            )
        },
        grupoSugerido = o.getString("grupoSugerido"),
        grupoDecidido = o.getBoolean("grupoDecidido"),
        grupoNome = o.getString("grupoNome"),
        cor = CorGrupo.porId(o.getInt("cor")),
        corDecidida = o.getBoolean("corDecidida"),
        avisos = o.optJSONArray("avisos")?.let(::listaDeInteiros),
        duracaoPadrao = o.getInt("duracaoPadrao")
    )
} catch (e: Exception) {
    null
}
