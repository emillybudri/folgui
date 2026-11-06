package com.example.folgui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ============================================================================
// Assistente com a API do Gemini (HttpURLConnection + JSONObject). A IA nunca mexe nos dados:
// ela devolve o que entendeu e uma lista de mudanças; o app confere essa lista contra as
// escalas (verificarAcoes) e só aplica depois que a pessoa confirma.
// ============================================================================

private const val URL_API = "https://generativelanguage.googleapis.com/v1beta/models/"

/** Se o modelo principal estiver lotado ou sem cota, a conversa segue nestes, em ordem. */
private val MODELOS_RESERVA = listOf("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")

/** O áudio é gravado em AAC (ver GravadorAudio), formato aceito pelo Gemini. */
const val MIME_AUDIO = "audio/aac"

enum class TipoAcaoIA(val codigo: String) {
    FERIAS("ferias"),
    REMOVER_FERIAS("remover_ferias"),
    FOLGA("folga"),
    AUSENCIA("ausencia"),
    TRABALHO("trabalho"),
    HORARIO("horario"),
    REMOVER_MUDANCA("remover_mudanca"),
    TURNO("turno"),
    SONO("sono"),
    CRIAR_EVENTO("criar_evento"),
    EDITAR_EVENTO("editar_evento"),
    EXCLUIR_EVENTO("excluir_evento");

    companion object {
        fun porCodigo(codigo: String): TipoAcaoIA? = entries.firstOrNull { it.codigo == codigo }
    }
}

/** Uma mudança proposta pela IA. Na edição de evento, campo nulo = fica como está. */
data class AcaoIA(
    val tipo: TipoAcaoIA,
    val pessoaId: Int = Pessoa.ID_VOCE,
    val dia: Int? = null,
    val fimDia: Int? = null,
    val turno: Turno? = null,
    val eventoId: Int? = null,
    val titulo: String? = null,
    val horario: String? = null,
    val duracaoMin: Int? = null,
    val local: String? = null,
    val participantes: Set<Int>? = null,
    val antecedenciaMin: Int? = null
)

data class MensagemIA(
    val deUsuario: Boolean,
    val texto: String,
    val acoes: List<AcaoIA> = emptyList(),
    val situacao: SituacaoAcoes = SituacaoAcoes.PENDENTE,
    /** O pedido nas palavras da IA, para a pessoa conferir antes de confirmar. */
    val entendi: String = "",
    /** Problemas que o próprio app encontrou nas mudanças (sono, trabalho, conflito...). */
    val avisos: List<String> = emptyList(),
    /** Frases das ações, feitas na hora da proposta: depois de aplicar, o "antes" não existe mais. */
    val descricoes: List<String> = emptyList(),
    /** Duração da gravação, quando a mensagem veio por áudio. */
    val audioSegundos: Int? = null
)

enum class SituacaoAcoes { PENDENTE, APLICADAS, DESCARTADAS }

/** Resposta interpretada; [transcricao] traz o que a IA ouviu, quando o pedido foi por áudio. */
data class RespostaIA(val mensagem: MensagemIA, val transcricao: String = "")

/** Erro com mensagem pronta para mostrar ao usuário. */
class ErroIA(mensagem: String) : Exception(mensagem)

class AssistenteIA(private val chave: String, private val modelo: String) {

    /** Envia o pedido (texto ou áudio em base64) com o contexto e devolve a resposta. */
    suspend fun conversar(
        contexto: String,
        historico: List<MensagemIA>,
        todas: List<Pessoa>,
        eventos: List<Evento>,
        pedido: String? = null,
        audioBase64: String? = null
    ): RespostaIA = withContext(Dispatchers.IO) {
        if (chave.isBlank()) throw ErroIA("A chave do Gemini não foi configurada. Coloque GEMINI_API_KEY no arquivo .env e gere o app de novo.")

        var ultimoErro = ""
        // Picos de demanda atingem vários modelos ao mesmo tempo: depois de uma pausa, tenta outra volta
        repeat(VOLTAS) { volta ->
            if (volta > 0) delay(PAUSA_ENTRE_VOLTAS_MS)
            for (atual in (listOf(modelo) + MODELOS_RESERVA).distinct()) {
                var estruturado = true
                while (true) {
                    val (codigo, corpo) = try {
                        enviar(atual, montarCorpo(contexto, historico, todas, eventos, pedido, audioBase64, estruturado).toString())
                    } catch (e: java.net.SocketTimeoutException) {
                        ultimoErro = "A IA demorou demais para responder. Tente de novo."
                        break
                    }
                    when {
                        codigo == 200 -> return@withContext interpretarResposta(extrairTexto(corpo))
                        // Um modelo pode recusar o esquema; as instruções sozinhas já pedem o mesmo JSON
                        codigo == 400 && estruturado && "API_KEY" !in corpo -> estruturado = false
                        // Lotado, sem cota ou aposentado: o próximo modelo resolve
                        codigo == 429 || codigo == 404 || codigo >= 500 -> {
                            ultimoErro = mensagemDeErro(codigo, corpo)
                            break
                        }
                        else -> throw ErroIA(mensagemDeErro(codigo, corpo))
                    }
                }
            }
        }
        throw ErroIA(ultimoErro)
    }

    private fun enviar(modeloAtual: String, corpo: String): Pair<Int, String> {
        val conexao = URL("$URL_API$modeloAtual:generateContent").openConnection() as HttpURLConnection
        try {
            conexao.requestMethod = "POST"
            conexao.connectTimeout = 15_000
            conexao.readTimeout = 45_000
            conexao.doOutput = true
            conexao.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conexao.setRequestProperty("x-goog-api-key", chave)
            conexao.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }

            val codigo = conexao.responseCode
            val fluxo = if (codigo in 200..299) conexao.inputStream else conexao.errorStream
            return codigo to (fluxo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            conexao.disconnect()
        }
    }

    private fun mensagemDeErro(codigo: Int, corpo: String): String = when {
        codigo == 400 && "API_KEY" in corpo -> "A chave do Gemini é inválida. Confira o GEMINI_API_KEY no arquivo .env."
        codigo == 401 || codigo == 403 -> "O Gemini recusou a chave. Confira o GEMINI_API_KEY no arquivo .env."
        codigo == 404 -> "Nenhum modelo do Gemini está disponível. Troque o GEMINI_MODEL no arquivo .env."
        codigo == 429 -> "Limite de uso do Gemini atingido. Espere um minuto e tente de novo."
        codigo >= 500 -> "O Gemini está sobrecarregado agora. Tente de novo em instantes."
        else -> "Não foi possível falar com a IA (erro $codigo)."
    }

    private companion object {
        const val VOLTAS = 2
        const val PAUSA_ENTRE_VOLTAS_MS = 4000L
    }
}

// ----------------------------------------------------------------------------
// Montagem do pedido
// ----------------------------------------------------------------------------

/** Texto de uma mensagem antiga, com as mudanças propostas, para a IA entender "não, às 15h". */
private fun textoDoHistorico(m: MensagemIA, todas: List<Pessoa>, eventos: List<Evento>): String {
    if (m.acoes.isEmpty()) return m.texto
    val situacao = when (m.situacao) {
        SituacaoAcoes.PENDENTE -> "aguardando confirmação"
        SituacaoAcoes.APLICADAS -> "aplicadas pela pessoa"
        SituacaoAcoes.DESCARTADAS -> "recusadas pela pessoa, que disse não ser isso"
    }
    return m.texto + "\n[Mudanças propostas ($situacao): " +
        m.acoes.indices.joinToString("; ") { i ->
            m.descricoes.getOrNull(i) ?: descreverAcao(m.acoes[i], todas, eventos)
        } + "]"
}

internal fun montarCorpo(
    contexto: String,
    historico: List<MensagemIA>,
    todas: List<Pessoa>,
    eventos: List<Evento>,
    pedido: String?,
    audioBase64: String?,
    estruturado: Boolean = true
): JSONObject {
    val conteudos = JSONArray()
    fun texto(t: String) = JSONArray().put(JSONObject().put("text", t))

    // A conversa precisa começar com a pessoa; guarda o bastante para corrigir um pedido anterior
    historico.takeLast(10).dropWhile { !it.deUsuario }.forEach { m ->
        conteudos.put(
            JSONObject()
                .put("role", if (m.deUsuario) "user" else "model")
                .put("parts", texto(textoDoHistorico(m, todas, eventos)))
        )
    }

    val partes = JSONArray()
    if (!pedido.isNullOrBlank()) partes.put(JSONObject().put("text", pedido))
    if (audioBase64 != null) {
        partes.put(JSONObject().put("text", "O pedido está neste áudio. Transcreva em \"transcricao\" e atenda."))
        partes.put(JSONObject().put("inlineData", JSONObject().put("mimeType", MIME_AUDIO).put("data", audioBase64)))
    }
    conteudos.put(JSONObject().put("role", "user").put("parts", partes))

    val config = JSONObject().put("responseMimeType", "application/json")
    if (estruturado) {
        config.put("responseJsonSchema", esquemaResposta())
        config.put("thinkingConfig", JSONObject().put("thinkingLevel", "MEDIUM"))
    }
    return JSONObject()
        .put("systemInstruction", JSONObject().put("parts", texto(INSTRUCOES + "\n\n" + contexto)))
        .put("contents", conteudos)
        .put("generationConfig", config)
}

private fun esquemaResposta(): JSONObject {
    fun tipo(t: String) = JSONObject().put("type", t)
    val codigos = JSONArray().apply { TipoAcaoIA.entries.forEach { put(it.codigo) } }
    val acao = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("tipo", tipo("string").put("enum", codigos))
                .put("pessoa_id", tipo("integer"))
                .put("dia", tipo("string"))
                .put("inicio", tipo("string"))
                .put("fim", tipo("string"))
                .put("entrada", tipo("string"))
                .put("saida", tipo("string"))
                .put("evento_id", tipo("integer"))
                .put("titulo", tipo("string"))
                .put("horario", tipo("string"))
                .put("duracao_min", tipo("integer"))
                .put("local", tipo("string"))
                .put("participantes", tipo("array").put("items", tipo("integer")))
                .put("lembrete_min", tipo("integer"))
        )
        // Obrigatórios para o modelo não esquecer o horário; quando não se aplicam, vêm vazios
        .put("required", JSONArray().put("tipo").put("titulo").put("dia").put("horario").put("evento_id"))
    return JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("transcricao", tipo("string"))
                .put("entendi", tipo("string"))
                .put("resposta", tipo("string"))
                .put("acoes", tipo("array").put("items", acao))
        )
        .put("required", JSONArray().put("entendi").put("resposta").put("acoes"))
}

private val INSTRUCOES = """
    Você é a assistente do Folgui, app de escalas de trabalho, folgas e compromissos. Fale em português do Brasil, de forma curta, calorosa e direta.

    COMO PENSAR, sempre, antes de responder:
    1. Descubra o que a pessoa quer de verdade, inclusive em falas informais ou com erros de transcrição.
    2. Converta datas relativas usando os dados abaixo ("amanhã", "sexta" = a próxima sexta depois de hoje) e confira se o dia da semana bate com a data.
    3. Pergunta sobre a escala ou a agenda: responda com os dados abaixo, dizendo dia da semana e data. Nunca invente; se o dado não estiver aqui, diga isso. Perguntas não geram ações.
    4. Faltou algo essencial, ou há mais de uma leitura possível (dois eventos parecidos, compromisso sem horário, "às 8" sem saber se é manhã ou noite)? NÃO proponha ação: pergunte em "resposta", com opções concretas, e deixe "acoes" vazio. Na dúvida, pergunte.
    5. Antes de propor, confira o resultado nos dados: não marque nada durante o sono, o trabalho, o descanso do plantão ou por cima de outro evento. Se o pedido cair aí, avise e ofereça o horário livre mais próximo. Só proponha mesmo assim se a pessoa insistir claramente.
    6. Depois de propor, releia: cada ação corresponde ao pedido, sem nada a mais e sem nada faltando?
    7. Se a pessoa corrigir ou recusar uma proposta anterior, proponha de novo por completo, já corrigida.

    Responda só com este JSON:
    {"transcricao":"","entendi":"","resposta":"","acoes":[]}
    - "transcricao": se o pedido veio em áudio, exatamente o que foi dito. Áudio inaudível: peça para repetir e deixe "acoes" vazio.
    - "entendi": uma frase com o que você entendeu, já com dia da semana, data e hora.
    - "resposta": a resposta para a pessoa. Se houver ações, peça para ela conferir. Nada é aplicado sem a confirmação dela, então nunca diga que já marcou, agendou ou alterou algo: diga o que vai fazer quando ela confirmar.
    - Toda ação vai com todos os campos do modelo abaixo. "criar_evento" sem "titulo", "dia" e "horario" não vale, nem "editar_evento" ou "excluir_evento" sem o "evento_id" da lista. Use evento_id 0 só nas ações que não são de um evento existente.

    Ações (datas AAAA-MM-DD, horas HH:MM, pessoa_id da lista abaixo; -1 é a própria pessoa, o "eu"):
    {"tipo":"criar_evento","titulo":"...","dia":"...","horario":"15:00","duracao_min":60,"local":"","participantes":[ids de quem vai além dela; omita se for só ela],"lembrete_min":60}
    {"tipo":"editar_evento","evento_id":1, e só os campos que mudam: "titulo","dia","horario","duracao_min","local","participantes","lembrete_min"} (é assim que se remarca qualquer evento que tenha evento_id)
    {"tipo":"excluir_evento","evento_id":1}
    {"tipo":"sono","entrada":"23:00","saida":"07:00"} (hora de dormir e de acordar)
    {"tipo":"ferias","pessoa_id":-1,"inicio":"...","fim":"..."} | {"tipo":"remover_ferias","pessoa_id":-1}
    {"tipo":"folga","pessoa_id":-1,"dia":"..."} (folga em dia de trabalho) | {"tipo":"ausencia","pessoa_id":-1,"dia":"..."} (atestado ou falta)
    {"tipo":"trabalho","pessoa_id":-1,"dia":"...","entrada":"08:00","saida":"14:00"} (trabalho em dia de folga)
    {"tipo":"horario","pessoa_id":-1,"dia":"...","entrada":"...","saida":"..."} (outro horário em dia de trabalho)
    {"tipo":"remover_mudanca","pessoa_id":-1,"dia":"..."} (volta à escala normal no dia)
    {"tipo":"turno","pessoa_id":-1,"entrada":"18:00","saida":"06:00"} (novo horário fixo da escala)
    Troca de plantão = "folga" no dia antigo + "trabalho" no dia novo, com o horário da escala.
    Use a lista de feriados para datas como "Natal". Datas sem ano são a próxima vez que acontecem a partir de hoje, nunca no passado.
    Só o que está na lista "Google Agenda" é somente leitura: para mudar esses, diga que é preciso abrir na Google Agenda. O que está na lista "Eventos do Folgui" você pode editar, remarcar e excluir.
""".trimIndent()

// ----------------------------------------------------------------------------
// Leitura da resposta
// ----------------------------------------------------------------------------

/** Texto da resposta do Gemini, ignorando as partes de raciocínio. */
internal fun extrairTexto(corpo: String): String {
    val json = try {
        JSONObject(corpo)
    } catch (e: Exception) {
        throw ErroIA("A IA devolveu uma resposta inesperada. Tente de novo.")
    }
    val candidato = json.optJSONArray("candidates")?.optJSONObject(0)
        ?: throw ErroIA("A IA não respondeu a esse pedido. Tente com outras palavras.")
    val partes = candidato.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
    val texto = (0 until partes.length())
        .mapNotNull { partes.optJSONObject(it) }
        .filter { !it.optBoolean("thought") }
        .joinToString("") { it.optString("text") }
    if (texto.isBlank()) throw ErroIA("A IA não respondeu a esse pedido. Tente com outras palavras.")
    return texto
}

/** Lê o JSON combinado com a IA. Se vier texto solto, mostra como resposta, sem ações. */
internal fun interpretarResposta(texto: String): RespostaIA {
    val inicio = texto.indexOf('{')
    val fim = texto.lastIndexOf('}')
    val json = try {
        if (inicio < 0 || fim <= inicio) null else JSONObject(texto.substring(inicio, fim + 1))
    } catch (e: Exception) {
        null
    } ?: return RespostaIA(MensagemIA(deUsuario = false, texto = texto.trim()))

    val acoes = mutableListOf<AcaoIA>()
    val lista = json.optJSONArray("acoes") ?: JSONArray()
    for (i in 0 until lista.length()) {
        lerAcao(lista.optJSONObject(i) ?: continue)?.takeIf(::acaoCompleta)?.let(acoes::add)
    }
    val incompletas = lista.length() - acoes.size
    val resposta = json.optString("resposta").ifBlank { "Confira se é isso mesmo." }
    return RespostaIA(
        mensagem = MensagemIA(
            deUsuario = false,
            // Sem ação válida, a resposta original pediria para confirmar algo que não existe
            texto = if (incompletas > 0 && acoes.isEmpty()) {
                "Não consegui montar essa mudança por completo. Pode repetir dizendo o dia e o horário?"
            } else {
                resposta
            },
            acoes = acoes,
            entendi = json.optString("entendi").trim(),
            avisos = if (incompletas > 0 && acoes.isNotEmpty()) {
                listOf("Parte do pedido ficou de fora porque veio incompleta. Confira se falta algo.")
            } else {
                emptyList()
            }
        ),
        transcricao = json.optString("transcricao").trim()
    )
}

private fun lerAcao(a: JSONObject): AcaoIA? {
    val tipo = TipoAcaoIA.porCodigo(a.optString("tipo")) ?: return null
    val entrada = a.optString("entrada")
    val saida = a.optString("saida")
    val turno = if (horarioValido(entrada) && horarioValido(saida)) {
        Turno(minutosDoHorario(entrada), minutosDoHorario(saida))
    } else {
        null
    }
    fun inteiro(campo: String): Int? = if (a.has(campo) && !a.isNull(campo)) a.optInt(campo) else null
    // Campo vazio significa "não mudar", nunca "apagar"
    fun textoOuNulo(campo: String): String? =
        if (a.has(campo) && !a.isNull(campo)) a.optString(campo).trim().ifEmpty { null } else null
    return AcaoIA(
        tipo = tipo,
        pessoaId = a.optInt("pessoa_id", Pessoa.ID_VOCE),
        dia = isoParaDia(a.optString("dia").ifEmpty { a.optString("inicio") })?.let(::proximaOcorrencia),
        fimDia = isoParaDia(a.optString("fim"))?.let(::proximaOcorrencia),
        turno = turno,
        eventoId = inteiro("evento_id")?.takeIf { it > 0 },
        titulo = textoOuNulo("titulo")?.take(60),
        horario = (textoOuNulo("horario") ?: textoOuNulo("entrada"))?.takeIf(::horarioValido),
        duracaoMin = inteiro("duracao_min")?.takeIf { it in 15..MINUTOS_DIA },
        local = textoOuNulo("local")?.take(60),
        // A própria pessoa sempre vai: a lista guarda só os outros
        participantes = a.optJSONArray("participantes")?.let { p ->
            (0 until p.length()).map(p::optInt).filter { it != Pessoa.ID_VOCE }.toSet()
        },
        antecedenciaMin = inteiro("lembrete_min")?.takeIf { m -> OPCOES_LEMBRETE.any { it.minutos == m } }
    )
}

/** Datas que a IA colocou no passado (ex.: "janeiro" com o ano atual) vão para o próximo ano. */
private fun proximaOcorrencia(dia: Int): Int {
    // Até duas semanas atrás vale (ex.: registrar um atestado recente)
    if (dia >= hoje() - 14) return dia
    val p = partes(dia)
    val ano = partes(hoje()).ano + if (diaDe(partes(hoje()).ano, p.mes, minOf(p.dia, 28)) < hoje()) 1 else 0
    return diaDe(ano, p.mes, minOf(p.dia, diasNoMes(ano, p.mes)))
}

private fun acaoCompleta(a: AcaoIA): Boolean = when (a.tipo) {
    TipoAcaoIA.FERIAS -> a.dia != null && a.fimDia != null && a.fimDia >= a.dia
    TipoAcaoIA.REMOVER_FERIAS -> true
    TipoAcaoIA.FOLGA, TipoAcaoIA.AUSENCIA, TipoAcaoIA.REMOVER_MUDANCA -> a.dia != null
    TipoAcaoIA.TRABALHO, TipoAcaoIA.HORARIO -> a.dia != null && a.turno != null
    TipoAcaoIA.TURNO, TipoAcaoIA.SONO -> a.turno != null
    TipoAcaoIA.CRIAR_EVENTO -> a.titulo != null && a.dia != null && a.horario != null
    TipoAcaoIA.EDITAR_EVENTO -> a.eventoId != null && listOf(
        a.titulo, a.dia, a.horario, a.duracaoMin, a.local, a.participantes, a.antecedenciaMin
    ).any { it != null }
    TipoAcaoIA.EXCLUIR_EVENTO -> a.eventoId != null
}

// ----------------------------------------------------------------------------
// Conferência: o app revê o que a IA propôs usando os dados reais
// ----------------------------------------------------------------------------

/** Como o evento ficaria depois da ação (null se a ação não cria nem altera evento). */
fun eventoResultante(a: AcaoIA, eventos: List<Evento>): Evento? = when (a.tipo) {
    TipoAcaoIA.CRIAR_EVENTO -> Evento(
        titulo = a.titulo.orEmpty(),
        dia = a.dia ?: hoje(),
        horario = a.horario ?: "20:00",
        local = a.local.orEmpty(),
        antecedenciaMin = a.antecedenciaMin ?: 60,
        duracaoMin = a.duracaoMin ?: 60,
        // Sem participantes informados, o compromisso é só de quem pediu
        participantes = a.participantes ?: emptySet()
    )
    TipoAcaoIA.EDITAR_EVENTO -> eventos.firstOrNull { it.id == a.eventoId }?.let { e ->
        e.copy(
            titulo = a.titulo ?: e.titulo,
            dia = a.dia ?: e.dia,
            horario = a.horario ?: e.horario,
            local = a.local ?: e.local,
            antecedenciaMin = a.antecedenciaMin ?: e.antecedenciaMin,
            duracaoMin = a.duracaoMin ?: e.duracaoMin,
            participantes = a.participantes ?: e.participantes
        )
    }
    else -> null
}

/**
 * Tira o que não pode ser aplicado (pessoa ou evento que não existem) e devolve avisos sobre
 * o que pode ser engano. Não depende da IA: é a garantia de que o pedido faz sentido.
 */
fun verificarAcoes(acoes: List<AcaoIA>, todas: List<Pessoa>, eventos: List<Evento>): Pair<List<AcaoIA>, List<String>> {
    val avisos = mutableListOf<String>()
    val validas = acoes.filter { a ->
        val deEvento = a.tipo == TipoAcaoIA.EDITAR_EVENTO || a.tipo == TipoAcaoIA.EXCLUIR_EVENTO
        when {
            deEvento && eventos.none { it.id == a.eventoId } -> {
                avisos.add("Não achei o evento que a IA citou, então deixei essa mudança de fora.")
                false
            }
            !deEvento && a.tipo != TipoAcaoIA.CRIAR_EVENTO && todas.none { it.id == a.pessoaId } -> {
                avisos.add("Não achei a pessoa que a IA citou, então deixei essa mudança de fora.")
                false
            }
            else -> true
        }
    }

    validas.forEach { a ->
        val pessoa = todas.firstOrNull { it.id == a.pessoaId }
        val dia = a.dia
        when (a.tipo) {
            TipoAcaoIA.CRIAR_EVENTO, TipoAcaoIA.EDITAR_EVENTO -> {
                val e = eventoResultante(a, eventos) ?: return@forEach
                if (e.jaPassou) avisos.add("${diaResumido(e.dia)} às ${e.horario} já passou.")
                pessoasDoEvento(e, todas).forEach { p ->
                    val quem = if (p.ehVoce) "Você estará" else "${p.nomeCurto} estará"
                    when (p.motivoOcupado(e.dia, e.janela)) {
                        TipoBloco.SONO -> avisos.add("$quem dormindo nesse horário (${p.sono?.resumo}).")
                        TipoBloco.TRABALHO -> avisos.add("$quem trabalhando nesse horário.")
                        TipoBloco.DESCANSO -> avisos.add("$quem descansando do plantão nesse horário.")
                        null -> Unit
                    }
                }
                eventosEmConflito(eventos, e.dia, e.janela, e.id).forEach {
                    avisos.add("Coincide com \"${it.titulo}\" (${it.faixaHorario}).")
                }
            }
            TipoAcaoIA.FOLGA, TipoAcaoIA.AUSENCIA -> if (pessoa != null && dia != null && pessoa.turnoNoDia(dia) == null) {
                avisos.add("${diaResumido(dia)} já é dia livre para ${pessoa.nomeCurto}.")
            }
            TipoAcaoIA.TRABALHO -> if (pessoa != null && dia != null && pessoa.turnoNoDia(dia) != null) {
                avisos.add("${pessoa.nomeCurto} já trabalha em ${diaResumido(dia)}: o horário será trocado.")
            }
            TipoAcaoIA.HORARIO -> if (pessoa != null && dia != null && pessoa.turnoPelaEscala(dia) == null) {
                avisos.add("${diaResumido(dia)} é folga na escala de ${pessoa.nomeCurto}.")
            }
            TipoAcaoIA.FERIAS -> if (pessoa?.temFerias == true) {
                avisos.add("Isso substitui as férias já cadastradas de ${pessoa.nomeCurto}.")
            }
            else -> Unit
        }
    }
    return validas to avisos.distinct()
}

// ----------------------------------------------------------------------------
// Contexto e descrição das ações
// ----------------------------------------------------------------------------

/** Quantos dias de escala a IA enxerga em detalhe. */
private const val DIAS_CONTEXTO = 45

/** Resumo enviado à IA: pessoas, escala dia a dia, sono, eventos, agenda e feriados. */
fun contextoParaIA(
    todas: List<Pessoa>,
    eventos: List<Evento>,
    agenda: Map<Int, List<CompromissoAgenda>>,
    feriados: Map<Int, String>
): String {
    val diaHoje = hoje()
    return buildString {
        appendLine("DADOS ATUAIS")
        appendLine("Agora: ${nomeDiaSemana(diaHoje)} ${diaParaIso(diaHoje)} ${horarioDeMinutos(minutoDoDiaAtual())}.")
        todas.forEach { p ->
            append("id ${p.id} ${p.nome}${if (p.ehVoce) " (a própria pessoa, \"eu\")" else ""}: ${p.resumoEscala}")
            if (p.descansoMin > 0) append(", descansa ${p.descansoMin / 60}h após a noite")
            if (p.temFerias) append(", férias ${diaParaIso(p.feriasInicio!!)} a ${diaParaIso(p.feriasFim!!)}")
            if (p.ehVoce) append(p.sono?.let { ", dorme ${it.resumo}" } ?: ", horário de sono não informado")
            append(". Dias: ")
            append((0 until DIAS_CONTEXTO).joinToString("; ") { i ->
                val d = diaHoje + i
                val estado = when (p.status(d)) {
                    StatusDia.FERIAS -> "férias"
                    StatusDia.TRABALHO -> "trab ${p.turnosNoDia(d).joinToString(" e ") { it.resumo }}"
                    StatusDia.FOLGA -> "folga"
                }
                "${diaParaIso(d).substring(5)} ${diaSemanaCurto(d)} $estado" + (p.excecao(d)?.let { " (${it.tipo.rotulo})" } ?: "")
            })
            appendLine(".")
        }
        val relevantes = eventos.filter { it.dia >= diaHoje - 14 }
        if (relevantes.isEmpty()) {
            appendLine("Eventos do Folgui: nenhum.")
        } else {
            appendLine("Eventos do Folgui (podem ser editados, remarcados e excluídos):")
            relevantes.forEach { e ->
                val quem = pessoasDoEvento(e, todas).joinToString(", ") { it.nomeCurto }
                appendLine(
                    "evento_id ${e.id}: \"${e.titulo}\" ${diaSemanaCurto(e.dia)} ${diaParaIso(e.dia)} ${e.faixaHorario}" +
                        (if (e.local.isNotBlank()) ", ${e.local}" else "") + ", com $quem" +
                        if (e.jaPassou) " (já passou)" else ""
                )
            }
        }
        val compromissos = agenda.filterKeys { it in diaHoje..diaHoje + 30 }.toSortedMap()
        if (compromissos.isNotEmpty()) {
            appendLine("Google Agenda (somente leitura):")
            compromissos.forEach { (d, lista) ->
                lista.forEach { appendLine("${diaSemanaCurto(d)} ${diaParaIso(d)} ${it.horario}: \"${it.titulo}\"") }
            }
        }
        val proximos = feriados.filterKeys { it in diaHoje..diaHoje + 365 }.toSortedMap()
        if (proximos.isNotEmpty()) {
            appendLine("Feriados: " + proximos.entries.joinToString("; ") { "${diaParaIso(it.key)} ${it.value}" } + ".")
        }
    }
}

/** Frase da ação para a pessoa conferir antes de aplicar, com o antes e o depois. */
fun descreverAcao(acao: AcaoIA, todas: List<Pessoa>, eventos: List<Evento>): String {
    val nome = todas.firstOrNull { it.id == acao.pessoaId }?.nomeCurto ?: "Pessoa desconhecida"
    val dia = acao.dia?.let(::diaResumido).orEmpty()
    val atual = eventos.firstOrNull { it.id == acao.eventoId }
    return when (acao.tipo) {
        TipoAcaoIA.FERIAS -> "$nome: férias de ${diaResumido(acao.dia!!)} a ${diaResumido(acao.fimDia!!)}"
        TipoAcaoIA.REMOVER_FERIAS -> "$nome: remover as férias"
        TipoAcaoIA.FOLGA -> "$nome: folga extra em $dia"
        TipoAcaoIA.AUSENCIA -> "$nome: atestado ou falta em $dia"
        TipoAcaoIA.TRABALHO -> "$nome: trabalho extra em $dia, ${acao.turno!!.resumo}"
        TipoAcaoIA.HORARIO -> "$nome: em $dia, trabalha ${acao.turno!!.resumo}"
        TipoAcaoIA.REMOVER_MUDANCA -> "$nome: volta à escala normal em $dia"
        TipoAcaoIA.TURNO -> "$nome: novo horário de trabalho, ${acao.turno!!.resumo}"
        TipoAcaoIA.SONO -> "Sono: dormir às ${horaCurta(acao.turno!!.inicio)} e acordar às ${horaCurta(acao.turno.fim)}"
        TipoAcaoIA.CRIAR_EVENTO -> eventoResultante(acao, eventos)!!.let {
            "Novo evento \"${it.titulo}\": ${diaResumido(it.dia)}, ${it.faixaHorario}"
        }
        TipoAcaoIA.EDITAR_EVENTO -> {
            val depois = eventoResultante(acao, eventos)
            if (atual == null || depois == null) {
                "Alterar evento"
            } else {
                "\"${atual.titulo}\": ${diaResumido(atual.dia)}, ${atual.faixaHorario} → " +
                    (if (depois.titulo != atual.titulo) "\"${depois.titulo}\", " else "") +
                    "${diaResumido(depois.dia)}, ${depois.faixaHorario}"
            }
        }
        TipoAcaoIA.EXCLUIR_EVENTO ->
            atual?.let { "Excluir \"${it.titulo}\" (${diaResumido(it.dia)}, ${it.faixaHorario})" } ?: "Excluir evento"
    }
}
