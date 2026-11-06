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
private val MODELOS_RESERVA = listOf("gemini-3-flash-preview", "gemini-flash-lite-latest", "gemini-3.1-flash-lite")

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
    EXCLUIR_EVENTO("excluir_evento"),
    CRIAR_GRUPO("criar_grupo"),
    EDITAR_GRUPO("editar_grupo"),
    EXCLUIR_GRUPO("excluir_grupo"),
    MOVER_EVENTOS("mover_eventos"),
    AVISOS_EVENTOS("avisos_eventos");

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
    val antecedenciaMin: Int? = null,
    /** Grupo do evento; [SEM_GRUPO_IA] tira o evento do grupo. */
    val grupoId: Int? = null,
    val cor: CorGrupo? = null,
    val eventoIds: List<Int>? = null,
    /** Lembretes em minutos; vazio em "avisos_eventos" tira todos. */
    val avisos: List<Int>? = null
)

/** Na resposta da IA, 0 é campo não usado, então "sem grupo" tem um número próprio. */
const val SEM_GRUPO_IA = -1

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
data class RespostaIA(val mensagem: MensagemIA, val transcricao: String = "", val ajuste: AjusteLote? = null)

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
        var pedidoAtual = pedido
        // Picos de demanda atingem vários modelos ao mesmo tempo: depois de uma pausa, tenta outra volta
        repeat(VOLTAS) { volta ->
            if (volta > 0) delay(PAUSA_ENTRE_VOLTAS_MS)
            for (atual in (listOf(modelo) + MODELOS_RESERVA).distinct()) {
                var estruturado = true
                while (true) {
                    val (codigo, corpo) = try {
                        enviar(atual, montarCorpo(contexto, historico, todas, eventos, pedidoAtual, audioBase64, estruturado).toString())
                    } catch (e: java.net.SocketTimeoutException) {
                        ultimoErro = "A IA demorou demais para responder. Tente de novo."
                        break
                    }
                    when {
                        codigo == 200 -> {
                            val resposta = interpretarResposta(extrairTexto(corpo))
                            if (!listaPerdida(pedido, resposta)) return@withContext resposta
                            // Uma insistência só: se a lista continuar sem vir, é melhor dizer isso
                            if (pedidoAtual != pedido) return@withContext resposta.copy(mensagem = resposta.mensagem.copy(texto = TEXTO_LISTA_PERDIDA))
                            pedidoAtual = pedido + NOTA_LISTA
                        }
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
            // Uma lista grande de compromissos leva mais tempo para voltar
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
        config.put("responseJsonSchema", esquemaResposta(comAudio = audioBase64 != null))
        // Em testes, "LOW" respondeu em 2 a 3 segundos, contra 6 a 10 do "MEDIUM"
        config.put("thinkingConfig", JSONObject().put("thinkingLevel", "LOW"))
    }
    return JSONObject()
        .put("systemInstruction", JSONObject().put("parts", texto(INSTRUCOES + "\n\n" + contexto)))
        .put("contents", conteudos)
        .put("generationConfig", config)
}

private fun esquemaResposta(comAudio: Boolean): JSONObject {
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
                .put("grupo_id", tipo("integer"))
                .put("cor", tipo("integer"))
                .put("evento_ids", tipo("array").put("items", tipo("integer")))
                .put("avisos_min", tipo("array").put("items", tipo("integer")))
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
                .put("lote", esquemaLote())
        )
        // Com áudio, a transcrição é obrigatória: é o que a pessoa lê no balão da conversa
        .put(
            "required",
            JSONArray().apply { if (comAudio) put("transcricao") }.put("entendi").put("resposta").put("acoes").put("lote")
        )
}

private fun esquemaLote(): JSONObject {
    fun tipo(t: String) = JSONObject().put("type", t)
    fun numeros() = tipo("array").put("items", tipo("integer"))
    val novo = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("titulo", tipo("string"))
                .put("profissional", tipo("string"))
                .put("categoria", tipo("string"))
                .put("dia", tipo("string"))
                .put("ano_informado", tipo("boolean"))
                .put("horario", tipo("string"))
                .put("duracao_min", tipo("integer"))
                .put("local", tipo("string"))
        )
        .put("required", JSONArray().put("titulo").put("dia").put("horario").put("ano_informado"))
    val edicao = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("n", tipo("integer"))
                .put("titulo", tipo("string"))
                .put("dia", tipo("string"))
                .put("horario", tipo("string"))
                .put("local", tipo("string"))
        )
        .put("required", JSONArray().put("n"))
    return JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("novos", tipo("array").put("items", novo))
                .put("grupo_sugerido", tipo("string"))
                .put("grupo", tipo("string"))
                .put("sem_grupo", tipo("boolean"))
                .put("cor", tipo("integer"))
                .put("manter_cor", tipo("boolean"))
                .put("avisos_min", numeros())
                .put("sem_avisos", tipo("boolean"))
                .put("itens_avisos", numeros())
                .put("duracao_min", tipo("integer"))
                .put("itens_duracao", numeros())
                .put("remover_itens", numeros())
                .put("manter_itens", numeros())
                .put("editar_itens", tipo("array").put("items", edicao))
                .put("confirmar", tipo("boolean"))
                .put("cancelar", tipo("boolean"))
        )
        // Com saída estruturada, o modelo omite o que é opcional, até o que a pessoa acabou de dizer
        .put(
            "required",
            JSONArray(
                listOf(
                    "novos", "grupo_sugerido", "grupo", "sem_grupo", "cor", "manter_cor", "avisos_min", "sem_avisos", "itens_avisos",
                    "duracao_min", "itens_duracao", "remover_itens", "manter_itens", "editar_itens", "confirmar", "cancelar"
                )
            )
        )
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
    {"transcricao":"","entendi":"","resposta":"","acoes":[],"lote":{}}
    - "lote" vem sempre, com todos os campos do modelo mais abaixo. Sem lista e sem plano em andamento, todos ficam vazios, false ou 0.
    - "transcricao": se o pedido veio em áudio, exatamente o que foi dito. Áudio inaudível: peça para repetir e deixe "acoes" vazio.
    - "entendi": uma frase com o que você entendeu, já com dia da semana, data e hora.
    - "resposta": a resposta para a pessoa. Se houver ações, peça para ela conferir. Nada é aplicado sem a confirmação dela, então nunca diga que já marcou, agendou ou alterou algo: diga o que vai fazer quando ela confirmar.
    - Toda ação vai com todos os campos do modelo abaixo. "criar_evento" sem "titulo", "dia" e "horario" não vale, nem "editar_evento" ou "excluir_evento" sem o "evento_id" da lista. Use evento_id 0 só nas ações que não são de um evento existente.

    Ações (datas AAAA-MM-DD, horas HH:MM, pessoa_id da lista abaixo; -1 é a própria pessoa, o "eu"):
    {"tipo":"criar_evento","titulo":"...","dia":"...","horario":"15:00","duracao_min":60,"local":"","participantes":[ids de quem vai além dela; omita se for só ela],"lembrete_min":60}
    {"tipo":"editar_evento","evento_id":1, e só os campos que mudam: "titulo","dia","horario","duracao_min","local","participantes","lembrete_min"} (é assim que se remarca qualquer evento que tenha evento_id)
    {"tipo":"excluir_evento","evento_id":1}
    {"tipo":"sono","entrada":"23:00","saida":"07:00"} (hora de dormir e de acordar; a pessoa precisa acordar antes de entrar no trabalho)
    {"tipo":"ferias","pessoa_id":-1,"inicio":"...","fim":"..."} | {"tipo":"remover_ferias","pessoa_id":-1}
    {"tipo":"folga","pessoa_id":-1,"dia":"..."} (folga em dia de trabalho) | {"tipo":"ausencia","pessoa_id":-1,"dia":"..."} (atestado ou falta)
    {"tipo":"trabalho","pessoa_id":-1,"dia":"...","entrada":"08:00","saida":"14:00"} (trabalho em dia de folga)
    {"tipo":"horario","pessoa_id":-1,"dia":"...","entrada":"...","saida":"..."} (outro horário em dia de trabalho)
    {"tipo":"remover_mudanca","pessoa_id":-1,"dia":"..."} (volta à escala normal no dia)
    {"tipo":"turno","pessoa_id":-1,"entrada":"18:00","saida":"06:00"} (novo horário fixo da escala)
    Troca de plantão = "folga" no dia antigo + "trabalho" no dia novo, com o horário da escala.
    Só preencha "duracao_min" se a pessoa disser a duração ou a hora de terminar: o fim do evento é opcional.
    Use a lista de feriados para datas como "Natal". Datas sem ano são a próxima vez que acontecem a partir de hoje, nunca no passado.
    Só o que está na lista "Google Agenda" é somente leitura: para mudar esses, diga que é preciso abrir na Google Agenda. O que está na lista "Eventos do Folgui" você pode editar, remarcar e excluir.

    GRUPOS são pastas de eventos, cada uma com uma cor que os eventos herdam. Evento sem grupo fica com a cor padrão da agenda. Cores (número: nome): 1 lilás, 2 verde-clara, 3 roxa, 4 rosa, 5 amarela, 6 laranja, 7 azul, 8 cinza, 9 azul-escura, 10 verde, 11 vermelha. Use só esses números.
    {"tipo":"criar_grupo","titulo":"Consultas","cor":11}
    {"tipo":"editar_grupo","grupo_id":1,"titulo":"novo nome","cor":3} (só o que muda; mudar a cor repinta todos os eventos do grupo)
    {"tipo":"excluir_grupo","grupo_id":1} (os eventos continuam, sem grupo)
    {"tipo":"mover_eventos","evento_ids":[1,2],"grupo_id":1} (grupo_id -1 tira do grupo)
    {"tipo":"avisos_eventos","evento_ids":[1,2],"avisos_min":[1440,60]} (troca os avisos desses eventos; lista vazia tira todos)
    "criar_evento" e "editar_evento" também aceitam "grupo_id" e "avisos_min". Minutos: 1 hora = 60, 1 dia = 1440, 1 semana = 10080. Máximo de 5 avisos, até 40320 minutos antes.

    VÁRIOS COMPROMISSOS NOVOS: com dois ou mais compromissos para criar de uma vez, NÃO use "acoes": preencha só "lote". Um compromisso só continua sendo "criar_evento".
    "lote":{"novos":[{"titulo":"...","profissional":"...","categoria":"...","dia":"AAAA-MM-DD","ano_informado":true,"horario":"16:12","duracao_min":50,"local":""}],"grupo_sugerido":"Consultas","grupo":"","sem_grupo":false,"cor":0,"manter_cor":false,"avisos_min":[],"sem_avisos":false,"itens_avisos":[],"duracao_min":0,"itens_duracao":[],"remover_itens":[],"manter_itens":[],"editar_itens":[{"n":1,"titulo":"","dia":"","horario":"","local":""}],"confirmar":false,"cancelar":false}
    - "novos": um item por compromisso, sem pular nenhum e na ordem da mensagem. "titulo" é o texto do compromisso exatamente como foi escrito, com nome, maiúsculas e parênteses; nunca corrija, resuma ou invente nomes, mesmo que pareçam ter erro de digitação. "profissional" e "categoria" são copiados do texto (ex.: "Dra. ANA" e "psicóloga") e ficam vazios se não estiverem lá.
    - Datas brasileiras são dia/mês/ano: 05/01/2027 é 5 de janeiro de 2027. Respeite o ano escrito, inclusive quando a lista vira o ano. "ano_informado" é true só se a pessoa escreveu o ano. "duracao_min" só se ela disse a duração ou a hora de terminar.
    - Compromisso sem data ou sem hora: deixe o campo vazio, que o app avisa a pessoa.
    - "grupo_sugerido": um nome curto para o conjunto, de preferência um grupo que já exista. Não é decisão da pessoa. "grupo" e "sem_grupo" só quando ELA disser. Um pedido completo traz tudo de uma vez, então preencha grupo, cor e avisos que ela já tiver dito.
    Com um PLANO EM ANDAMENTO nos dados, a fala da pessoa é sobre ele e vai em "lote" (nunca em "novos", a não ser que ela traga compromissos novos). Leia a "Última pergunta feita à pessoa" para entender respostas curtas:
    - Sobre o grupo sugerido: "sim" = "grupo" com o nome sugerido; "não" = "sem_grupo":true.
    - Sobre manter a cor: "sim" = "manter_cor":true. "muda para azul" = "cor":7. Se a pergunta for da cor e dos avisos juntos, um "sim, 1 dia antes" responde as duas coisas.
    - Sobre avisos: "1 dia antes e 1 hora antes" = "avisos_min":[1440,60]; "não" = "sem_avisos":true. "só nos três primeiros" = o mesmo em "avisos_min" com "itens_avisos":[1,2,3].
    - "tira o 2" = "remover_itens":[2]. "só os três primeiros" (sem falar de avisos) = "manter_itens":[1,2,3]. "o 3 é às 15h" = "editar_itens". "todas de 50 minutos" = "duracao_min":50; só em algumas, com "itens_duracao".
    - "confirmar":true só quando a pessoa mandar criar agora ("pode criar", "confirmo") e a última pergunta for a da prévia. "cancelar":true se ela desistir.
    Em "resposta", com "lote", escreva só uma frase curta: o app monta as perguntas e a prévia. Nunca diga que já criou.
""".trimIndent()

// ----------------------------------------------------------------------------
// Leitura da resposta
// ----------------------------------------------------------------------------

private val DATA_NO_TEXTO = Regex("""\b\d{1,2}/\d{1,2}(/\d{2,4})?\b""")
private val HORA_NO_TEXTO = Regex("""\b\d{1,2}(:\d{2}|h\d{0,2})\b""")
private const val NOTA_LISTA = "\n\n(Esta mensagem traz vários compromissos: preencha lote.novos com todos eles.)"
private const val TEXTO_LISTA_PERDIDA = "Não consegui montar a lista dessa vez, e nada foi criado. Pode mandar de novo?"

/**
 * A mensagem traz várias datas com hora, mas a resposta não trouxe compromisso nenhum? Sem isto,
 * a pessoa leria "encontrei 8 consultas" sem ter nada para conferir.
 */
internal fun listaPerdida(pedido: String?, resposta: RespostaIA): Boolean {
    if (pedido == null || resposta.mensagem.acoes.isNotEmpty() || resposta.ajuste?.novos?.isNotEmpty() == true) return false
    return DATA_NO_TEXTO.findAll(pedido).count() >= 2 && HORA_NO_TEXTO.findAll(pedido).count() >= 2
}

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
    val ajuste = json.optJSONObject("lote")?.let(::lerAjuste)?.takeIf { it.mudaPlano || it.confirmar || it.cancelar }
    if (ajuste != null) {
        // A lista vai para a prévia: nada dela passa pela confirmação de ações comuns
        return RespostaIA(
            MensagemIA(deUsuario = false, texto = json.optString("resposta").trim(), entendi = json.optString("entendi").trim()),
            transcricao = json.optString("transcricao").trim(),
            ajuste = ajuste
        )
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

private fun numeros(a: JSONArray?): List<Int> = a?.let { lista -> (0 until lista.length()).map(lista::optInt) }.orEmpty()

/** Número da cor vindo da IA; 0 ou qualquer outro valor fora da paleta é ignorado. */
private fun corDaIA(a: JSONObject): CorGrupo? = CorGrupo.porId(a.optInt("cor"))

/**
 * Um compromisso sozinho não é lista: segue pela confirmação de sempre, sem perguntas de grupo e
 * avisos. Com plano aberto ele entra no plano, e grupo novo só o plano sabe criar.
 */
internal fun semLoteDeUmSo(r: RespostaIA, temPlano: Boolean, grupos: List<Grupo>): RespostaIA {
    val ajuste = r.ajuste ?: return r
    val item = ajuste.novos.singleOrNull() ?: return r
    val grupo = grupoPorNome(grupos, ajuste.grupo)
    if (temPlano || (ajuste.grupo.isNotBlank() && grupo == null)) return r
    val acao = AcaoIA(
        tipo = TipoAcaoIA.CRIAR_EVENTO,
        dia = item.dia,
        titulo = item.titulo,
        horario = item.horario,
        duracaoMin = item.duracaoMin ?: ajuste.duracaoMin,
        local = item.local,
        grupoId = grupo?.id,
        avisos = ajuste.avisos
    )
    return r.copy(mensagem = r.mensagem.copy(texto = r.mensagem.texto.ifBlank { "Confira se é isso mesmo." }, acoes = listOf(acao)), ajuste = null)
}

private fun lerAjuste(o: JSONObject): AjusteLote {
    val lista = o.optJSONArray("novos") ?: JSONArray()
    val novos = (0 until lista.length()).mapNotNull { i ->
        val n = lista.optJSONObject(i) ?: return@mapNotNull null
        val titulo = n.optString("titulo").trim().take(TAMANHO_TITULO)
        val horario = n.optString("horario").trim()
        val dia = isoParaDia(n.optString("dia"))
        if (titulo.isEmpty() || dia == null || !horarioValido(horario)) return@mapNotNull null
        NovoItem(
            titulo = titulo,
            profissional = n.optString("profissional").trim(),
            categoria = n.optString("categoria").trim(),
            // Ano escrito pela pessoa nunca é trocado: se já passou, a prévia avisa
            dia = if (n.optBoolean("ano_informado")) dia else proximaOcorrencia(dia),
            horario = horarioDeMinutos(minutosDoHorario(horario)),
            local = n.optString("local").trim().take(TAMANHO_TITULO),
            duracaoMin = n.optInt("duracao_min").takeIf { it in 5..MINUTOS_DIA }
        )
    }
    val edicoes = o.optJSONArray("editar_itens") ?: JSONArray()
    return AjusteLote(
        novos = novos,
        incompletos = lista.length() - novos.size,
        grupoSugerido = o.optString("grupo_sugerido"),
        grupo = o.optString("grupo"),
        semGrupo = o.optBoolean("sem_grupo"),
        cor = corDaIA(o),
        manterCor = o.optBoolean("manter_cor"),
        avisos = numeros(o.optJSONArray("avisos_min")).takeIf { it.isNotEmpty() },
        semAvisos = o.optBoolean("sem_avisos"),
        itensAvisos = numeros(o.optJSONArray("itens_avisos")),
        duracaoMin = o.optInt("duracao_min").takeIf { it in 5..MINUTOS_DIA },
        itensDuracao = numeros(o.optJSONArray("itens_duracao")),
        remover = numeros(o.optJSONArray("remover_itens")),
        manter = numeros(o.optJSONArray("manter_itens")),
        editar = (0 until edicoes.length()).mapNotNull { i ->
            val e = edicoes.optJSONObject(i) ?: return@mapNotNull null
            EdicaoItem(
                n = e.optInt("n"),
                titulo = e.optString("titulo").trim().take(TAMANHO_TITULO).ifEmpty { null },
                dia = isoParaDia(e.optString("dia")),
                horario = e.optString("horario").trim().takeIf(::horarioValido),
                local = e.optString("local").trim().ifEmpty { null }
            ).takeIf { it.n > 0 && listOf(it.titulo, it.dia, it.horario, it.local).any { campo -> campo != null } }
        },
        confirmar = o.optBoolean("confirmar"),
        cancelar = o.optBoolean("cancelar")
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
        titulo = textoOuNulo("titulo")?.take(TAMANHO_TITULO),
        horario = (textoOuNulo("horario") ?: textoOuNulo("entrada"))?.takeIf(::horarioValido),
        duracaoMin = inteiro("duracao_min")?.takeIf { it in 15..MINUTOS_DIA },
        local = textoOuNulo("local")?.take(60),
        // A própria pessoa sempre vai: a lista guarda só os outros
        participantes = a.optJSONArray("participantes")?.let { p ->
            (0 until p.length()).map(p::optInt).filter { it != Pessoa.ID_VOCE }.toSet()
        },
        antecedenciaMin = inteiro("lembrete_min")?.takeIf { m -> OPCOES_LEMBRETE.any { it.minutos == m } },
        grupoId = inteiro("grupo_id")?.takeIf { it != 0 },
        cor = corDaIA(a),
        eventoIds = a.optJSONArray("evento_ids")?.let(::numeros),
        avisos = a.optJSONArray("avisos_min")?.let { normalizarAvisos(numeros(it)) }
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
        a.titulo, a.dia, a.horario, a.duracaoMin, a.local, a.participantes, a.antecedenciaMin, a.grupoId, a.avisos?.ifEmpty { null }
    ).any { it != null }
    TipoAcaoIA.EXCLUIR_EVENTO -> a.eventoId != null
    TipoAcaoIA.CRIAR_GRUPO -> a.titulo != null && a.cor != null
    TipoAcaoIA.EDITAR_GRUPO -> (a.grupoId ?: 0) > 0 && (a.titulo != null || a.cor != null)
    TipoAcaoIA.EXCLUIR_GRUPO -> (a.grupoId ?: 0) > 0
    TipoAcaoIA.MOVER_EVENTOS -> !a.eventoIds.isNullOrEmpty() && a.grupoId != null
    TipoAcaoIA.AVISOS_EVENTOS -> !a.eventoIds.isNullOrEmpty() && a.avisos != null
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
        duracaoMin = a.duracaoMin ?: SEM_FIM,
        // Sem participantes informados, o compromisso é só de quem pediu
        participantes = a.participantes ?: emptySet(),
        grupoId = a.grupoId?.coerceAtLeast(0) ?: 0
    ).let { e -> if (a.avisos.isNullOrEmpty()) e else e.comAvisos(a.avisos) }
    TipoAcaoIA.EDITAR_EVENTO -> eventos.firstOrNull { it.id == a.eventoId }?.let { e ->
        e.copy(
            titulo = a.titulo ?: e.titulo,
            dia = a.dia ?: e.dia,
            horario = a.horario ?: e.horario,
            local = a.local ?: e.local,
            antecedenciaMin = a.antecedenciaMin ?: e.antecedenciaMin,
            duracaoMin = a.duracaoMin ?: e.duracaoMin,
            participantes = a.participantes ?: e.participantes,
            grupoId = a.grupoId?.coerceAtLeast(0) ?: e.grupoId,
            // Aviso único pedido pela IA substitui os outros
            avisosExtras = if (a.antecedenciaMin != null) emptyList() else e.avisosExtras
        ).let { novo -> if (a.avisos.isNullOrEmpty()) novo else novo.comAvisos(a.avisos) }
    }
    else -> null
}

/**
 * Tira o que não pode ser aplicado (pessoa ou evento que não existem) e devolve avisos sobre
 * o que pode ser engano. Não depende da IA: é a garantia de que o pedido faz sentido.
 */
fun verificarAcoes(
    acoes: List<AcaoIA>,
    todas: List<Pessoa>,
    eventos: List<Evento>,
    grupos: List<Grupo> = emptyList()
): Pair<List<AcaoIA>, List<String>> {
    val avisos = mutableListOf<String>()
    val validas = acoes.filter { a ->
        val deEvento = a.tipo == TipoAcaoIA.EDITAR_EVENTO || a.tipo == TipoAcaoIA.EXCLUIR_EVENTO
        val citaGrupo = (a.grupoId ?: 0) > 0
        when {
            citaGrupo && grupos.none { it.id == a.grupoId } -> {
                avisos.add("Não achei o grupo que a IA citou, então deixei essa mudança de fora.")
                false
            }
            a.eventoIds?.any { id -> eventos.none { it.id == id } } == true -> {
                avisos.add("Não achei um dos eventos que a IA citou, então deixei essa mudança de fora.")
                false
            }
            a.tipo == TipoAcaoIA.CRIAR_GRUPO && erroNomeGrupo(a.titulo.orEmpty(), grupos) != null -> {
                avisos.add("Já existe o grupo ${a.titulo}.")
                false
            }
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
            TipoAcaoIA.SONO -> a.turno?.let { sono ->
                todas.firstOrNull { it.ehVoce }?.let { avisoSono(sono, it.todosTurnos) }?.let(avisos::add)
            }
            TipoAcaoIA.FERIAS -> if (pessoa?.temFerias == true) {
                avisos.add("Isso substitui as férias já cadastradas de ${pessoa.nomeCurto}.")
            }
            TipoAcaoIA.EDITAR_GRUPO -> grupos.firstOrNull { it.id == a.grupoId }?.let { g ->
                val quantos = eventosDoGrupo(eventos, g.id).size
                if (a.cor != null && a.cor != g.cor && quantos > 0) {
                    avisos.add("${plural(quantos, "evento")} do grupo ${g.nome} vão mudar para a cor ${a.cor.comum}.")
                }
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

/** Nomes longos de profissionais precisam caber inteiros no título do evento. */
const val TAMANHO_TITULO = 120

/** Resumo enviado à IA: pessoas, escala dia a dia, sono, eventos, agenda e feriados. */
fun contextoParaIA(
    todas: List<Pessoa>,
    eventos: List<Evento>,
    agenda: Map<Int, List<CompromissoAgenda>>,
    feriados: Map<Int, String>,
    grupos: List<Grupo> = emptyList(),
    plano: PlanoLote? = null
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
                        (grupoDoEvento(e, grupos)?.let { ", grupo ${it.nome}" }.orEmpty()) +
                        (if (e.avisos.isNotEmpty()) ", avisos ${rotuloAvisos(e.avisos)}" else "") +
                        if (e.jaPassou) " (já passou)" else ""
                )
            }
        }
        if (grupos.isEmpty()) {
            appendLine("Grupos: nenhum.")
        } else {
            appendLine("Grupos:")
            grupos.forEach { g ->
                appendLine("grupo_id ${g.id}: ${g.nome}, cor ${g.cor.id} (${g.cor.comum}), ${plural(eventosDoGrupo(eventos, g.id).size, "evento")}")
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
        if (plano != null && !plano.concluido) append(contextoDoPlano(plano, grupos))
    }
}

/** Frase da ação para a pessoa conferir antes de aplicar, com o antes e o depois. */
fun descreverAcao(
    acao: AcaoIA,
    todas: List<Pessoa>,
    eventos: List<Evento>,
    grupos: List<Grupo> = emptyList()
): String {
    val grupo = grupos.firstOrNull { it.id == acao.grupoId }
    val quantos = plural(acao.eventoIds.orEmpty().size, "evento")
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
        TipoAcaoIA.CRIAR_GRUPO -> "Novo grupo ${acao.titulo}, cor ${acao.cor?.comum}"
        TipoAcaoIA.EDITAR_GRUPO -> "Grupo ${grupo?.nome}: " + listOfNotNull(
            acao.titulo?.let { "novo nome $it" },
            acao.cor?.let { "cor ${it.comum}, com os eventos dele" }
        ).joinToString(", ")
        TipoAcaoIA.EXCLUIR_GRUPO -> "Excluir o grupo ${grupo?.nome}. Os eventos continuam, sem grupo"
        TipoAcaoIA.MOVER_EVENTOS -> if (grupo != null) "Mover $quantos para o grupo ${grupo.nome}" else "Tirar $quantos do grupo"
        TipoAcaoIA.AVISOS_EVENTOS -> "Avisos de $quantos: ${rotuloAvisos(acao.avisos.orEmpty())}"
    }
}
