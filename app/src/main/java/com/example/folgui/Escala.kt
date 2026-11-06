package com.example.folgui

/** Horários de turno e de eventos são guardados em minutos desde a meia-noite. */
const val MINUTOS_DIA = 1440

/** Tamanho máximo do ciclo de uma escala personalizada. */
const val MAX_DIAS_CICLO = 28

// Dias da semana em bits (domingo = bit 0 ... sábado = bit 6)
const val SEG_A_SEX = 0b0111110

// ============================================================================
// Turno: horário de entrada e de saída (a saída pode ser no dia seguinte)
// ============================================================================
data class Turno(val inicio: Int, val fim: Int) {
    /** Duração em minutos (entrada igual à saída = plantão de 24h). */
    val duracao: Int
        get() = Math.floorMod(fim - inicio, MINUTOS_DIA).let { if (it == 0) MINUTOS_DIA else it }

    /** Termina no dia seguinte (ex.: das 18h às 6h). */
    val noturno: Boolean get() = inicio + duracao > MINUTOS_DIA

    /** "6h às 18h". */
    val resumo: String get() = "${horaCurta(inicio)} às ${horaCurta(fim)}"

    /** "12 horas", "8h30". */
    val duracaoTexto: String
        get() = if (duracao % 60 == 0) "${duracao / 60} horas" else "${duracao / 60}h${"%02d".format(duracao % 60)}"

    /** "Trabalha das 18h às 6h do dia seguinte". */
    val frase: String
        get() = when {
            duracao == MINUTOS_DIA -> "Plantão de 24h a partir das ${horaCurta(inicio)}"
            noturno -> "Trabalha das ${horaCurta(inicio)} às ${horaCurta(fim)} do dia seguinte"
            else -> "Trabalha das ${horaCurta(inicio)} às ${horaCurta(fim)}"
        }

    /** Versão curta para caber na célula do calendário: "8–17h", "18:30–6h", "24h". */
    val compacto: String
        get() {
            if (duracao == MINUTOS_DIA) return "24h"
            fun hora(m: Int) = if (m % 60 == 0) "${m / 60}" else "${m / 60}:${"%02d".format(m % 60)}"
            return "${hora(inicio)}–${hora(fim)}" + if (fim % 60 == 0) "h" else ""
        }

    val codigo: String get() = "$inicio-$fim"

    companion object {
        fun deCodigo(texto: String): Turno? {
            val p = texto.split("-")
            val inicio = p.getOrNull(0)?.toIntOrNull() ?: return null
            val fim = p.getOrNull(1)?.toIntOrNull() ?: return null
            if (inicio !in 0 until MINUTOS_DIA || fim !in 0 until MINUTOS_DIA) return null
            return Turno(inicio, fim)
        }
    }
}

/** Padrão do ciclo em texto para o banco: "420-1140;F;420-1140;F". */
fun padraoParaTexto(padrao: List<Turno?>): String = padrao.joinToString(";") { it?.codigo ?: "F" }

fun textoParaPadrao(texto: String): List<Turno?>? {
    if (texto.isBlank()) return null
    val dias = texto.split(";").map { if (it == "F") null else Turno.deCodigo(it) ?: return null }
    return dias.takeIf { it.size in 1..MAX_DIAS_CICLO }
}

// ============================================================================
// Tipos de escala: modelos que preenchem o ciclo automaticamente
// ============================================================================
enum class TipoEscala(
    val codigo: String,
    val titulo: String,
    val descricao: String,
    val diasTrabalho: Int,
    val diasFolga: Int,
    val turnoPadrao: Turno
) {
    SEMANAL(
        "semanal", "Mesmos dias da semana",
        "Ex.: segunda a sexta",
        0, 0, Turno(8 * 60, 17 * 60)
    ),
    ESCALA_12X36(
        "12x36", "Dia sim, dia não",
        "12x36",
        1, 1, Turno(7 * 60, 19 * 60)
    ),
    ESCALA_24X48(
        "24x48", "Plantão de 24h",
        "24x48",
        1, 2, Turno(7 * 60, 7 * 60)
    ),
    ESCALA_2X2(
        "2x2", "2 dias sim, 2 não",
        "2x2",
        2, 2, Turno(7 * 60, 19 * 60)
    ),
    ESCALA_4X2(
        "4x2", "4 dias sim, 2 não",
        "4x2",
        4, 2, Turno(8 * 60, 17 * 60)
    ),
    ESCALA_5X1(
        "5x1", "5 dias sim, 1 não",
        "5x1",
        5, 1, Turno(8 * 60, 17 * 60)
    ),
    ESCALA_6X1(
        "6x1", "Folga que muda",
        "6x1",
        6, 1, Turno(8 * 60, 17 * 60)
    ),
    PERSONALIZADA(
        "personalizada", "Do seu jeito",
        "Ciclo de até $MAX_DIAS_CICLO dias",
        0, 0, Turno(7 * 60, 19 * 60)
    );

    /** Plantão ou revezamento com padrão fixo (12x36, 2x2...). */
    val revezamento: Boolean get() = this != SEMANAL && this != PERSONALIZADA

    val nome: String
        get() = when (this) {
            SEMANAL -> "Semanal"
            PERSONALIZADA -> "Personalizada"
            else -> codigo
        }

    /** Ciclo da escala com um horário só (semanal: segunda a sexta). */
    fun padraoCom(turno: Turno): List<Turno?> = when (this) {
        SEMANAL -> List(7) { d -> turno.takeIf { SEG_A_SEX and (1 shl d) != 0 } }
        PERSONALIZADA -> listOf(turno, turno, null, null)
        else -> List(diasTrabalho) { turno } + List(diasFolga) { null }
    }

    /** Horários sugeridos para preencher com um toque. */
    val turnosSugeridos: List<Turno>
        get() = when (this) {
            ESCALA_12X36, ESCALA_2X2, PERSONALIZADA -> listOf(
                Turno(7 * 60, 19 * 60), Turno(19 * 60, 7 * 60), Turno(6 * 60, 18 * 60), Turno(18 * 60, 6 * 60)
            )
            ESCALA_24X48 -> listOf(Turno(7 * 60, 7 * 60), Turno(8 * 60, 8 * 60))
            else -> listOf(
                Turno(8 * 60, 17 * 60), Turno(8 * 60, 18 * 60), Turno(6 * 60, 14 * 60),
                Turno(14 * 60, 22 * 60), Turno(22 * 60, 6 * 60)
            )
        }

    /** Uma semana de exemplo (0 = folga, 1 = turno A, 2 = turno B), desenhada no cartão. */
    val exemplo: List<Int>
        get() = when (this) {
            SEMANAL -> listOf(1, 1, 1, 1, 1, 0, 0)
            PERSONALIZADA -> listOf(1, 1, 2, 2, 0, 0, 0)
            else -> List(7) { if (it % (diasTrabalho + diasFolga) < diasTrabalho) 1 else 0 }
        }

    companion object {
        // Versões antigas gravavam "5x2": agora é uma escala semanal
        fun porCodigo(codigo: String?): TipoEscala =
            entries.firstOrNull { it.codigo == codigo } ?: SEMANAL
    }
}

/** Modelos prontos da escala personalizada ("A" = turno A, "B" = turno B, "F" = folga). */
data class ModeloPersonalizado(val nome: String, val dias: String, val turnoA: Turno, val turnoB: Turno?)

val MODELOS_PERSONALIZADOS = listOf(
    ModeloPersonalizado("2 dias, 2 noites, 4 folgas", "AABBFFFF", Turno(7 * 60, 19 * 60), Turno(19 * 60, 7 * 60)),
    ModeloPersonalizado("4 dias e 4 folgas", "AAAAFFFF", Turno(7 * 60, 19 * 60), null),
    ModeloPersonalizado("3 noites e 3 folgas", "AAAFFF", Turno(19 * 60, 7 * 60), null),
    ModeloPersonalizado("12x36 com sábado livre", "AFAFAFF", Turno(7 * 60, 19 * 60), null)
)

/** Dias da semana de uma antiga escala 5x2: os cinco dias a partir do início do ciclo. */
fun diasSemanaDe5x2(inicioCiclo: Int): Int =
    (0 until 5).fold(0) { mascara, i -> mascara or (1 shl diaDaSemana(inicioCiclo + i)) }

/** Converte o formato antigo (tipo, dias da semana e um turno) para o ciclo novo. */
fun padraoLegado(escala: TipoEscala, diasSemana: Int, turno: Turno): List<Turno?> =
    if (escala == TipoEscala.SEMANAL) {
        List(7) { d -> turno.takeIf { diasSemana and (1 shl d) != 0 } }
    } else {
        escala.padraoCom(turno)
    }

/** "seg a sex", "seg, qua e sex", "todos os dias". */
fun resumoDiasSemana(mascara: Int): String {
    // Ordem de leitura: segunda primeiro, domingo por último
    val ordem = listOf(1, 2, 3, 4, 5, 6, 0)
    val marcados = ordem.filter { mascara and (1 shl it) != 0 }
    val posicoes = marcados.map { ordem.indexOf(it) }
    return when {
        marcados.isEmpty() -> "nenhum dia"
        marcados.size == 7 -> "todos os dias"
        marcados.size >= 3 && posicoes.last() - posicoes.first() == marcados.size - 1 ->
            "${nomeCurtoDiaSemana(marcados.first())} a ${nomeCurtoDiaSemana(marcados.last())}"
        marcados.size == 1 -> nomeCurtoDiaSemana(marcados[0])
        else -> marcados.dropLast(1).joinToString(", ") { nomeCurtoDiaSemana(it) } +
            " e " + nomeCurtoDiaSemana(marcados.last())
    }
}

enum class StatusDia(val rotulo: String, val livre: Boolean) {
    TRABALHO("Trabalha", false),
    FOLGA("Folga", true),
    FERIAS("Férias", true)
}

// ============================================================================
// Exceções: trocas de plantão, folgas e trabalhos fora da escala
// ============================================================================
enum class TipoExcecao(val codigo: String, val rotulo: String, val trabalha: Boolean) {
    FOLGA("folga", "Folga extra", false),
    AUSENCIA("ausencia", "Atestado ou falta", false),
    TRABALHO("trabalho", "Trabalho extra", true),
    HORARIO("horario", "Horário diferente", true),
    FERIADO("feriado", "Trabalho no feriado", true);

    companion object {
        fun porCodigo(codigo: String): TipoExcecao? = entries.firstOrNull { it.codigo == codigo }
    }
}

data class Excecao(val dia: Int, val tipo: TipoExcecao, val turno: Turno?)

enum class TipoAtividade(val codigo: String, val rotulo: String, val botao: String, val exemplo: String, val turnoInicial: Turno) {
    EMPREGO("emprego", "Outro emprego", "Emprego", "Ex.: Hospital", Turno(480, 1020)),
    FACULDADE("faculdade", "Faculdade", "Faculdade", "Ex.: Unicamp", Turno(1140, 1320));

    companion object {
        fun porCodigo(codigo: String?): TipoAtividade? = entries.firstOrNull { it.codigo == codigo }
    }
}

/** Segundo emprego, faculdade ou outra rotina fixa, com escala própria. */
data class Atividade(
    val tipo: TipoAtividade,
    val nome: String,
    val escala: TipoEscala,
    val inicioCiclo: Int,
    val padrao: List<Turno?>
) {
    /** A atividade usa as mesmas regras de ciclo da escala principal. */
    fun comoPessoa() = Pessoa(0, nome, escala, inicioCiclo, padrao)

    fun turnoNoDia(dia: Int): Turno? = comoPessoa().turnoPelaEscala(dia)

    val resumo: String get() = comoPessoa().resumoEscala
}

/** Trecho de um dia, em minutos desde a meia-noite (o fim pode passar de 24h). */
data class Janela(val inicio: Int, val duracao: Int) {
    val fim: Int get() = inicio + duracao
}

/** O que ocupa um trecho do dia, do mais para o menos importante. */
enum class TipoBloco { TRABALHO, DESCANSO, SONO }

/** Trecho ocupado em minutos contados desde 01/01/1970 (trabalho, descanso do plantão ou sono). */
private data class Bloco(val inicio: Int, val fim: Int, val tipo: TipoBloco)

// ============================================================================
// Pessoa: escala em ciclo + férias + exceções
// ============================================================================

/** Escala de uma pessoa. O id -1 representa o próprio usuário (salvo no DataStore). */
data class Pessoa(
    val id: Int,
    val nome: String,
    val escala: TipoEscala,
    val inicioCiclo: Int,
    val padrao: List<Turno?> = escala.padraoCom(escala.turnoPadrao),
    val feriasInicio: Int? = null,
    val feriasFim: Int? = null,
    val email: String = "",
    val descansoMin: Int = 0,
    val excecoes: Map<Int, Excecao> = emptyMap(),
    /** Horário de dormir (início) e de acordar (fim). Nada é sugerido nesse trecho. */
    val sono: Turno? = null,
    /** Outro emprego, faculdade e afins, além da escala principal. */
    val atividades: List<Atividade> = emptyList(),
    /** A empresa costuma folgar em feriados? Se não, o app nem pergunta. */
    val folgaFeriados: Boolean = true
) {
    val ehVoce: Boolean get() = id == ID_VOCE

    val temFerias: Boolean get() = feriasInicio != null && feriasFim != null

    /** Nome usado em frases: "Você trabalha", "Tia Márcia trabalha". */
    val nomeCurto: String get() = if (ehVoce) "Você" else nome

    /** Horários diferentes usados na escala (no máximo dois). */
    val turnos: List<Turno> get() = padrao.filterNotNull().distinct()

    val turnoNoturno: Boolean get() = turnos.any { it.noturno }

    /** "6h às 18h", "7h às 19h e 19h às 7h". */
    val resumoTurno: String
        get() = when (turnos.size) {
            0 -> "sem turnos"
            1 -> turnos[0].resumo
            2 -> "${turnos[0].resumo} e ${turnos[1].resumo}"
            else -> "horários variados"
        }

    /** Dias da semana com trabalho (escala semanal). */
    val diasSemana: Int
        get() = if (escala == TipoEscala.SEMANAL) {
            padrao.indices.fold(0) { m, d -> if (padrao[d] != null) m or (1 shl d) else m }
        } else {
            0
        }

    /** "Seg a sex · 8h às 17h", "Escala 12x36 · 18h às 6h", "Ciclo de 8 dias · ...". */
    val resumoEscala: String
        get() = when (escala) {
            TipoEscala.SEMANAL ->
                if (diasSemana == 0) "Sem dias de trabalho" else "${resumoDiasSemana(diasSemana).capitalizar()} · $resumoTurno"
            TipoEscala.PERSONALIZADA -> "Ciclo de ${padrao.size} dias · $resumoTurno"
            else -> "Escala ${escala.codigo} · $resumoTurno"
        }

    /** Posição do dia no ciclo (a semanal é presa ao dia da semana). */
    fun indiceNoCiclo(dia: Int): Int =
        if (escala == TipoEscala.SEMANAL) diaDaSemana(dia) else Math.floorMod(dia - inicioCiclo, padrao.size)

    /** Turno do dia pela escala, sem férias nem exceções. */
    fun turnoPelaEscala(dia: Int): Turno? = if (padrao.isEmpty()) null else padrao.getOrNull(indiceNoCiclo(dia))

    fun emFerias(dia: Int): Boolean = feriasInicio != null && feriasFim != null && dia in feriasInicio..feriasFim

    /** Turno que começa no dia, considerando férias e exceções. */
    fun turnoNoDia(dia: Int): Turno? {
        if (emFerias(dia)) return null
        excecoes[dia]?.let { return it.turno }
        return turnoPelaEscala(dia)
    }

    /** Outras atividades do dia. Férias, folga e atestado liberam o dia inteiro. */
    fun atividadesNoDia(dia: Int): List<Pair<Atividade, Turno>> {
        if (emFerias(dia) || excecoes[dia]?.tipo?.trabalha == false) return emptyList()
        return atividades.mapNotNull { a -> a.turnoNoDia(dia)?.let { a to it } }
    }

    /** Tudo que começa no dia: a escala principal e as outras atividades. */
    fun turnosNoDia(dia: Int): List<Turno> = listOfNotNull(turnoNoDia(dia)) + atividadesNoDia(dia).map { it.second }

    /** Mudança registrada no dia (ignorada durante as férias). */
    fun excecao(dia: Int): Excecao? = if (emFerias(dia)) null else excecoes[dia]

    fun status(dia: Int): StatusDia = when {
        emFerias(dia) -> StatusDia.FERIAS
        turnosNoDia(dia).isNotEmpty() -> StatusDia.TRABALHO
        else -> StatusDia.FOLGA
    }

    /** Trabalho que começa no dia, o descanso depois de um plantão noturno e o sono daquela noite. */
    private fun blocosDoDia(dia: Int): List<Bloco> = buildList {
        turnoNoDia(dia)?.let { turno ->
            val inicio = dia * MINUTOS_DIA + turno.inicio
            val fim = inicio + turno.duracao
            add(Bloco(inicio, fim, TipoBloco.TRABALHO))
            if (turno.noturno && descansoMin > 0) add(Bloco(fim, fim + descansoMin, TipoBloco.DESCANSO))
        }
        atividadesNoDia(dia).forEach { (_, turno) ->
            val inicio = dia * MINUTOS_DIA + turno.inicio
            add(Bloco(inicio, inicio + turno.duracao, TipoBloco.TRABALHO))
        }
        sono?.let {
            val inicio = dia * MINUTOS_DIA + it.inicio
            add(Bloco(inicio, inicio + it.duracao, TipoBloco.SONO))
        }
    }

    /** Blocos que podem tocar o intervalo (um plantão de 24h + descanso alcança até dois dias depois). */
    private fun blocosEntre(inicio: Int, fim: Int): List<Bloco> =
        (Math.floorDiv(inicio, MINUTOS_DIA) - 2..Math.floorDiv(fim, MINUTOS_DIA))
            .flatMap(::blocosDoDia)
            .filter { it.inicio < fim && inicio < it.fim }

    /** Livre no dia (folga ou férias) ou, se houver [janela], livre naquele horário. */
    fun livre(dia: Int, janela: Janela? = null): Boolean =
        if (janela == null) {
            status(dia).livre
        } else {
            blocosEntre(dia * MINUTOS_DIA + janela.inicio, dia * MINUTOS_DIA + janela.fim).isEmpty()
        }

    /** O que impede a pessoa de estar livre na [janela] (null = livre). Trabalho pesa mais que sono. */
    fun motivoOcupado(dia: Int, janela: Janela): TipoBloco? =
        blocosEntre(dia * MINUTOS_DIA + janela.inicio, dia * MINUTOS_DIA + janela.fim).minOfOrNull { it.tipo }

    private fun recortes(dia: Int, tipo: TipoBloco): List<Pair<Int, Int>> {
        val base = dia * MINUTOS_DIA
        return blocosEntre(base, base + MINUTOS_DIA)
            .filter { it.tipo == tipo }
            .map { maxOf(it.inicio, base) - base to minOf(it.fim, base + MINUTOS_DIA) - base }
    }

    /** Trechos do dia em trabalho, inclusive o fim de um plantão que começou ontem. */
    fun periodosNoDia(dia: Int): List<Pair<Int, Int>> = recortes(dia, TipoBloco.TRABALHO)

    /** Trechos do dia em descanso depois de um plantão noturno. */
    fun descansoNoDia(dia: Int): List<Pair<Int, Int>> = recortes(dia, TipoBloco.DESCANSO)

    /** Trechos do dia em que a pessoa está dormindo. */
    fun sonoNoDia(dia: Int): List<Pair<Int, Int>> = recortes(dia, TipoBloco.SONO)

    /** "Trabalha das 6h às 18h", "Folga · sai do plantão às 6h e descansa até 12h", "Folga extra". */
    fun descricaoDia(dia: Int): String {
        if (emFerias(dia)) return "De férias"
        val mudanca = excecoes[dia]
        val turno = turnoNoDia(dia)
        val extras = atividadesNoDia(dia).joinToString(" · ") { (a, t) -> "${a.nome} ${t.resumo}" }
        if (turno != null) {
            val principal = if (mudanca != null) "${mudanca.tipo.rotulo} · ${turno.resumo}" else turno.frase
            return if (extras.isEmpty()) principal else "$principal · $extras"
        }
        if (mudanca != null) return if (extras.isEmpty()) mudanca.tipo.rotulo else "${mudanca.tipo.rotulo} · $extras"
        if (extras.isNotEmpty()) return extras
        val base = dia * MINUTOS_DIA
        val blocos = blocosEntre(base, base + MINUTOS_DIA)
        val fimPlantao = blocos.filter { it.tipo == TipoBloco.TRABALHO }.maxOfOrNull { it.fim }
        val fimDescanso = blocos.filter { it.tipo == TipoBloco.DESCANSO }.maxOfOrNull { it.fim }
        return when {
            fimPlantao != null && fimDescanso != null ->
                "Folga · sai do plantão às ${horaCurta(fimPlantao)} e descansa até ${horaCurta(fimDescanso)}"
            fimPlantao != null -> "Folga · sai do plantão às ${horaCurta(fimPlantao)}"
            fimDescanso != null -> "Folga · descansa até ${horaCurta(fimDescanso)}"
            else -> "Folga o dia todo"
        }
    }

    /** Situação neste instante: (ocupado?, texto). */
    fun situacaoAgora(): Pair<Boolean, String> {
        val agora = minutoAtual()
        val dia = Math.floorDiv(agora, MINUTOS_DIA)
        if (emFerias(dia)) return false to "De férias"
        val blocos = (dia - 2..dia + 1).flatMap(::blocosDoDia)
        val atual = blocos.filter { agora in it.inicio until it.fim }.minByOrNull { it.tipo }
        if (atual != null) {
            val amanha = if (Math.floorDiv(atual.fim, MINUTOS_DIA) > dia) " de amanhã" else ""
            return true to when (atual.tipo) {
                TipoBloco.TRABALHO -> "Trabalhando agora · sai às ${horaCurta(atual.fim)}$amanha"
                TipoBloco.DESCANSO -> "Descansando do plantão · até ${horaCurta(atual.fim)}$amanha"
                TipoBloco.SONO -> "Hora de dormir · acorda às ${horaCurta(atual.fim)}$amanha"
            }
        }
        val proximo = blocos.filter { it.tipo == TipoBloco.TRABALHO && it.inicio > agora }.minByOrNull { it.inicio }
        return false to when {
            proximo == null -> "Livre agora"
            Math.floorDiv(proximo.inicio, MINUTOS_DIA) == dia -> "Livre agora · entra às ${horaCurta(proximo.inicio)}"
            else -> "Livre agora · amanhã entra às ${horaCurta(proximo.inicio)}"
        }
    }

    /** Próximo dia (a partir de [aPartirDe], inclusive) em que a pessoa estará livre ou trabalhando. */
    fun proximoDia(aPartirDe: Int, livre: Boolean, limite: Int = 400): Int? =
        (aPartirDe until aPartirDe + limite).firstOrNull { status(it).livre == livre }

    companion object {
        const val ID_VOCE = -1
    }
}

// ============================================================================
// Disponibilidade de uma pessoa sozinha
// ============================================================================

/**
 * Situação do dia para quem marca algo sozinho. Separa "dia de trabalho, mas esse horário
 * está livre" de "dia de folga": sem isso, um evento às 20h deixaria todos os dias iguais.
 */
enum class DisponibilidadeSolo(val rotulo: String) {
    FOLGA("Folga"),
    FERIAS("Férias"),
    TRABALHA_LIVRE("Trabalho, horário livre"),
    OCUPADO("Horário ocupado")
}

fun Pessoa.disponibilidade(dia: Int, janela: Janela): DisponibilidadeSolo = when {
    !livre(dia, janela) -> DisponibilidadeSolo.OCUPADO
    status(dia) == StatusDia.FERIAS -> DisponibilidadeSolo.FERIAS
    status(dia) == StatusDia.TRABALHO -> DisponibilidadeSolo.TRABALHA_LIVRE
    else -> DisponibilidadeSolo.FOLGA
}

// ============================================================================
// Disponibilidade do grupo
// ============================================================================
fun todosLivres(pessoas: List<Pessoa>, dia: Int, janela: Janela? = null): Boolean =
    pessoas.all { it.livre(dia, janela) }

// Situação do grupo no dia (ou no horário): todos livres, só parte livre ou ninguém livre
enum class SituacaoGrupo(val rotulo: String) {
    TODOS_LIVRES("Todos livres"),
    PARCIAL("Parte livre"),
    NINGUEM_LIVRE("Ninguém livre")
}

fun situacaoGrupo(pessoas: List<Pessoa>, dia: Int, janela: Janela? = null): SituacaoGrupo {
    val livres = pessoas.count { it.livre(dia, janela) }
    return when (livres) {
        pessoas.size -> SituacaoGrupo.TODOS_LIVRES
        0 -> SituacaoGrupo.NINGUEM_LIVRE
        else -> SituacaoGrupo.PARCIAL
    }
}

/** Como o dia serve para um encontro, olhando o dia inteiro em vez de um horário suposto. */
enum class QualidadeDia(val rotulo: String, val situacao: SituacaoGrupo) {
    MELHOR("Melhor dia", SituacaoGrupo.TODOS_LIVRES),
    PARTE("Parte do dia", SituacaoGrupo.PARCIAL),
    NAO_DA("Sem horário", SituacaoGrupo.NINGUEM_LIVRE)
}

/**
 * Melhor = ninguém trabalha e sobra tempo; parte = alguém trabalha, mas há [duracao] minutos juntos;
 * sem horário = não há esse tempo livre em comum. Hoje só conta o que ainda não passou.
 */
fun qualidadeDia(
    pessoas: List<Pessoa>,
    dia: Int,
    duracao: Int = 60,
    diaHoje: Int = hoje(),
    minutoHoje: Int = minutoDoDiaAtual()
): QualidadeDia {
    val de = if (dia == diaHoje) maxOf(7 * 60, ((minutoHoje + 29) / 30) * 30) else 7 * 60
    return when {
        de >= MINUTOS_DIA || janelasLivres(pessoas, dia, de = de, minimo = duracao).isEmpty() -> QualidadeDia.NAO_DA
        pessoas.all { it.status(dia).livre } -> QualidadeDia.MELHOR
        else -> QualidadeDia.PARTE
    }
}

/** Próximos dias para um encontro: primeiro os melhores e, se faltarem, os que dão em parte do dia. */
fun melhoresDias(
    pessoas: List<Pessoa>,
    duracao: Int = 60,
    quantidade: Int = 5,
    horizonte: Int = 60,
    diaHoje: Int = hoje(),
    minutoHoje: Int = minutoDoDiaAtual()
): List<Int> =
    (diaHoje until diaHoje + horizonte)
        .map { it to qualidadeDia(pessoas, it, duracao, diaHoje, minutoHoje) }
        .filter { it.second != QualidadeDia.NAO_DA }
        .sortedBy { it.second.ordinal }
        .take(quantidade)
        .map { it.first }

/** Horário de quem ainda não tem nenhum turno cadastrado. */
val SONO_PADRAO = Turno(23 * 60, 7 * 60)

private const val HORAS_SONO_MIN = 8 * 60

/** Tempo para se arrumar e chegar: acordar menos que isso antes de entrar não dá. */
private const val PREPARO_MIN = 60

/** Margem que o aviso aceita entre acordar e entrar no trabalho. */
private const val MARGEM_ACORDAR_MIN = 30

/** Os dois trechos se cruzam em algum momento, inclusive virando a meia-noite? */
private fun Turno.sobrepoe(outro: Turno): Boolean = listOf(-MINUTOS_DIA, 0, MINUTOS_DIA).any { desvio ->
    val comeco = inicio + desvio
    comeco < outro.inicio + outro.duracao && outro.inicio < comeco + duracao
}

/** Todos os horários da pessoa: a escala e as outras atividades. */
val Pessoa.todosTurnos: List<Turno>
    get() = (turnos + atividades.flatMap { it.padrao.filterNotNull() }).distinct()

/**
 * Oito horas de sono que combinam com o trabalho: acorda uma hora antes de entrar.
 * Quem só trabalha à noite dorme depois de sair; quem sai tarde dorme depois do turno.
 */
fun sonoSugerido(turnos: List<Turno>): Turno {
    fun depoisDe(saida: Int) = Turno(Math.floorMod(saida + PREPARO_MIN, MINUTOS_DIA), Math.floorMod(saida + PREPARO_MIN + HORAS_SONO_MIN, MINUTOS_DIA))

    if (turnos.isEmpty()) return SONO_PADRAO
    // O plantão de 24h começa de manhã: quem faz plantão acorda antes dele, como no turno de dia
    val deDia = turnos.filter { !it.noturno || it.duracao == MINUTOS_DIA }
    if (deDia.isEmpty()) return depoisDe(turnos.minOf { it.fim })

    val acordar = minOf(SONO_PADRAO.fim, deDia.minOf { it.inicio } - PREPARO_MIN)
    val sono = Turno(Math.floorMod(acordar - HORAS_SONO_MIN, MINUTOS_DIA), Math.floorMod(acordar, MINUTOS_DIA))
    val conflito = turnos.firstOrNull { it.duracao < MINUTOS_DIA && it.sobrepoe(sono) }
    return if (conflito != null) depoisDe(conflito.fim) else sono
}

/** Frase de alerta quando o sono cai no horário de trabalho ou termina em cima da entrada. */
fun avisoSono(sono: Turno, turnos: List<Turno>): String? {
    val comMargem = Turno(sono.inicio, Math.floorMod(sono.fim + MARGEM_ACORDAR_MIN, MINUTOS_DIA))
    val turno = turnos.firstOrNull { it.sobrepoe(comMargem) } ?: return null
    return "Você entra às ${horaCurta(turno.inicio)}, mas acordaria às ${horaCurta(sono.fim)}."
}

/** Faltando quantos dias o feriado aparece na tela inicial. */
const val DIAS_AVISO_FERIADO = 7

/** Feriado ainda sem resposta, em dia de trabalho, para quem costuma folgar nessas datas. */
fun Pessoa.perguntarFeriado(dia: Int, diaHoje: Int = hoje()): Boolean =
    folgaFeriados && dia >= diaHoje && !emFerias(dia) && dia !in excecoes && turnoPelaEscala(dia) != null

/** O feriado que a tela inicial deve perguntar agora, se houver. */
fun Pessoa.feriadoParaPerguntar(feriados: Set<Int>, diaHoje: Int = hoje()): Int? =
    feriados.sorted().firstOrNull { it <= diaHoje + DIAS_AVISO_FERIADO && perguntarFeriado(it, diaHoje) }

/** Guarda a resposta como mudança no dia, assim o calendário e as sugestões já a levam em conta. */
fun Pessoa.respostaFeriado(dia: Int, trabalha: Boolean): Excecao =
    if (trabalha) {
        Excecao(dia, TipoExcecao.FERIADO, turnoPelaEscala(dia) ?: turnos.firstOrNull() ?: escala.turnoPadrao)
    } else {
        Excecao(dia, TipoExcecao.FOLGA, null)
    }

/** Por que todos estão livres: "Folga de ambos", "Você de férias · Tia de folga". */
fun motivoLivres(pessoas: List<Pessoa>, dia: Int): String {
    val situacoes = pessoas.map { it.status(dia) }
    if (pessoas.size == 1) return if (situacoes[0] == StatusDia.FERIAS) "De férias" else "Dia de folga"
    val grupo = if (pessoas.size == 2) "ambos" else "todos"
    return when {
        situacoes.all { it == StatusDia.FOLGA } -> "Folga de $grupo"
        situacoes.all { it == StatusDia.FERIAS } -> "Férias de $grupo"
        else -> pessoas.joinToString(" · ") {
            "${it.nomeCurto} de ${if (it.status(dia) == StatusDia.FERIAS) "férias" else "folga"}"
        }
    }
}

/** Próximos dias em que TODAS as pessoas estão livres (no dia todo ou na [janela]). */
fun diasEmComum(
    pessoas: List<Pessoa>,
    aPartirDe: Int,
    quantidade: Int,
    horizonte: Int = 180,
    janela: Janela? = null
): List<Int> {
    if (pessoas.isEmpty()) return emptyList()
    return (aPartirDe until aPartirDe + horizonte)
        .asSequence()
        .filter { todosLivres(pessoas, it, janela) }
        .take(quantidade)
        .toList()
}

/** Junta trechos que se sobrepõem, em ordem. */
fun mesclarPeriodos(periodos: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
    val resultado = mutableListOf<Pair<Int, Int>>()
    periodos.sortedBy { it.first }.forEach { (a, b) ->
        val ultimo = resultado.lastOrNull()
        if (ultimo != null && a <= ultimo.second) {
            resultado[resultado.lastIndex] = ultimo.first to maxOf(ultimo.second, b)
        } else {
            resultado.add(a to b)
        }
    }
    return resultado
}

/** Trechos do dia em que pelo menos uma pessoa está trabalhando, descansando do plantão ou dormindo. */
fun periodosOcupados(pessoas: List<Pessoa>, dia: Int): List<Pair<Int, Int>> =
    mesclarPeriodos(pessoas.flatMap { it.periodosNoDia(dia) + it.descansoNoDia(dia) + it.sonoNoDia(dia) })

/** Horários em que todos estão livres juntos (por padrão entre 7h e meia-noite, com pelo menos 1h). */
fun janelasLivres(
    pessoas: List<Pessoa>,
    dia: Int,
    de: Int = 7 * 60,
    ate: Int = MINUTOS_DIA,
    minimo: Int = 60
): List<Janela> {
    val janelas = mutableListOf<Janela>()
    var cursor = de
    periodosOcupados(pessoas, dia).forEach { (a, b) ->
        if (a > cursor) janelas.add(Janela(cursor, minOf(a, ate) - cursor))
        cursor = maxOf(cursor, b)
    }
    if (cursor < ate) janelas.add(Janela(cursor, ate - cursor))
    return janelas.filter { it.duracao >= minimo }
}

/** Um horário em que todos podem se encontrar. */
data class Encontro(val dia: Int, val janela: Janela)

// Períodos do dia usados na busca "Quando dá?"
enum class PeriodoDia(val rotulo: String, val de: Int, val ate: Int) {
    QUALQUER("Qualquer hora", 7 * 60, MINUTOS_DIA),
    MANHA("Manhã", 7 * 60, 12 * 60),
    TARDE("Tarde", 12 * 60, 18 * 60),
    NOITE("Noite", 18 * 60, MINUTOS_DIA)
}

/**
 * Próximos horários em que todas as [pessoas] estão livres por pelo menos [duracao] minutos.
 * Fica com o maior horário de cada dia; hoje só conta o que ainda não passou.
 */
fun proximosEncontros(
    pessoas: List<Pessoa>,
    duracao: Int,
    periodo: PeriodoDia = PeriodoDia.QUALQUER,
    soFimDeSemana: Boolean = false,
    feriados: Set<Int> = emptySet(),
    quantidade: Int = 12,
    horizonte: Int = 90
): List<Encontro> {
    val diaHoje = hoje()
    // Hoje começa na próxima meia hora cheia
    val agora = ((minutoDoDiaAtual() + 29) / 30) * 30
    return (diaHoje until diaHoje + horizonte).asSequence()
        .filter { !soFimDeSemana || diaDaSemana(it) == 0 || diaDaSemana(it) == 6 || it in feriados }
        .mapNotNull { d ->
            val de = if (d == diaHoje) maxOf(periodo.de, agora) else periodo.de
            if (de >= periodo.ate) return@mapNotNull null
            janelasLivres(pessoas, d, de, periodo.ate, duracao).maxByOrNull { it.duracao }?.let { Encontro(d, it) }
        }
        .take(quantidade)
        .toList()
}

// ============================================================================
// Eventos
// ============================================================================

// Antecedência do lembrete em minutos (-1 = sem lembrete)
data class OpcaoLembrete(val minutos: Int, val rotulo: String)

val OPCOES_LEMBRETE = listOf(
    OpcaoLembrete(-1, "Sem lembrete"),
    OpcaoLembrete(0, "No horário"),
    OpcaoLembrete(60, "1 hora antes"),
    OpcaoLembrete(1440, "1 dia antes")
)

fun rotuloLembrete(minutos: Int): String =
    OPCOES_LEMBRETE.firstOrNull { it.minutos == minutos }?.rotulo ?: "Sem lembrete"

/** Durações possíveis de um evento, em minutos. */
val OPCOES_DURACAO = listOf(60, 120, 180, 240, 360)

data class Evento(
    val id: Int = 0,
    val titulo: String,
    val dia: Int,
    val horario: String,
    val local: String,
    val antecedenciaMin: Int,
    val duracaoMin: Int = 120,
    /** Ids de quem vai além de você (null = todas as pessoas). */
    val participantes: Set<Int>? = null,
    /** Id deste evento na Google Agenda (0 = ainda não está lá). */
    val agendaId: Long = 0
) {
    val momento: Long get() = momentoEm(dia, horario)

    /** A pessoa escolheu só o início: o evento fica sem hora para acabar. */
    val semFim: Boolean get() = duracaoMin == SEM_FIM

    val momentoLembrete: Long? get() = if (antecedenciaMin < 0) null else momento - antecedenciaMin * 60_000L

    val jaPassou: Boolean get() = momento <= System.currentTimeMillis()

    /** Horário ocupado pelo evento no dia. */
    val janela: Janela get() = Janela(minutosDoHorario(horario), duracaoReservada(duracaoMin))

    /** "20:00 às 22:00", ou só "20:00" quando não tem fim. */
    val faixaHorario: String get() = if (semFim) horario else "$horario às ${horarioDeMinutos(janela.fim)}"
}

/** Duração de um evento que só tem início. */
const val SEM_FIM = 0

/** Sem fim definido, o evento segura uma hora da agenda para as sugestões não caírem em cima dele. */
fun duracaoReservada(duracao: Int): Int = if (duracao == SEM_FIM) 60 else duracao

/** Outros eventos que se sobrepõem a [janela] no [dia]. */
fun eventosEmConflito(eventos: List<Evento>, dia: Int, janela: Janela, ignorarId: Int = 0): List<Evento> =
    eventos.filter { it.id != ignorarId && it.dia == dia && it.janela.inicio < janela.fim && janela.inicio < it.janela.fim }

/** Um dia e horário possíveis para remarcar um evento. */
data class OpcaoRemarcar(val dia: Int, val horario: String, val motivo: String)

/**
 * Próximos horários em que quem vai está livre e sem outro evento. Prefere manter o horário
 * do evento; se não der, usa o primeiro horário livre do dia. Nunca cai em trabalho ou sono.
 */
fun sugestoesRemarcar(
    evento: Evento,
    pessoas: List<Pessoa>,
    eventos: List<Evento>,
    quantidade: Int = 4,
    horizonte: Int = 60,
    diaHoje: Int = hoje(),
    minutoHoje: Int = minutoDoDiaAtual()
): List<OpcaoRemarcar> {
    val duracao = evento.janela.duracao
    fun disponivel(dia: Int, inicio: Int): Boolean {
        val janela = Janela(inicio, duracao)
        return (dia != diaHoje || inicio > minutoHoje) &&
            !(dia == evento.dia && inicio == evento.janela.inicio) &&
            todosLivres(pessoas, dia, janela) &&
            eventosEmConflito(eventos, dia, janela, evento.id).isEmpty()
    }
    return (diaHoje until diaHoje + horizonte).asSequence().mapNotNull { dia ->
        val desde = if (dia == diaHoje) ((minutoHoje + 59) / 60) * 60 else 7 * 60
        val inicio = evento.janela.inicio.takeIf { disponivel(dia, it) }
            ?: janelasLivres(pessoas, dia, de = desde, minimo = duracao)
                .flatMap { j -> (j.inicio..j.fim - duracao step 30).toList() }
                .firstOrNull { disponivel(dia, it) }
            ?: return@mapNotNull null
        val folga = pessoas.all { it.status(dia).livre }
        OpcaoRemarcar(dia, horarioDeMinutos(inicio), if (folga) motivoLivres(pessoas, dia) else "Fora do horário de trabalho")
    }.take(quantidade).toList()
}

/** Você e quem vai ao evento. */
fun pessoasDoEvento(evento: Evento, todas: List<Pessoa>): List<Pessoa> {
    val ids = evento.participantes ?: return todas
    return todas.filter { it.ehVoce || it.id in ids }
}

fun participantesParaTexto(ids: Set<Int>?): String = ids?.sorted()?.joinToString(",") ?: "*"

fun textoParaParticipantes(texto: String): Set<Int>? =
    if (texto == "*") null else texto.split(",").mapNotNull { it.toIntOrNull() }.toSet()

/** Resumo de quem está livre no dia (ou no horário): (todos livres?, texto). */
fun resumoDisponibilidade(pessoas: List<Pessoa>, dia: Int, janela: Janela? = null): Pair<Boolean, String> {
    val ocupados = pessoas.filter { !it.livre(dia, janela) }.map { it.nomeCurto }
    val quando = if (janela != null) " nesse horário" else ""
    // Sozinha, a frase diz o motivo em vez de só "não está livre"
    if (pessoas.size == 1 && janela != null && ocupados.isNotEmpty()) {
        return false to when (pessoas[0].motivoOcupado(dia, janela)) {
            TipoBloco.SONO -> "Você estará dormindo nesse horário"
            TipoBloco.DESCANSO -> "Você estará descansando do plantão"
            else -> "Você estará trabalhando nesse horário"
        }
    }
    return when {
        ocupados.isEmpty() && pessoas.size <= 1 -> true to "Você está livre$quando"
        ocupados.isEmpty() -> true to "Todos estão livres$quando"
        pessoas.size <= 1 -> false to "Você não está livre$quando"
        ocupados.size == pessoas.size && pessoas.size > 2 -> false to "Ninguém está livre$quando"
        ocupados.size == 1 -> false to "${ocupados[0]} não está livre$quando"
        ocupados.size == 2 -> false to "${ocupados[0]} e ${ocupados[1]} não estão livres$quando"
        else -> false to "${ocupados[0]} e mais ${ocupados.size - 1} pessoas não estão livres$quando"
    }
}
