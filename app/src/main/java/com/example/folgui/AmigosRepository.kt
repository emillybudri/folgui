package com.example.folgui

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import kotlin.random.Random

// ============================================================================
// Amigos que também usam o Folgui: um código conecta as duas contas
// ============================================================================

/** Compromisso enviado por um amigo, à espera de resposta. */
data class Convite(
    val id: String,
    val deUid: String,
    val deNome: String,
    val titulo: String,
    val dia: Int,
    val horario: String,
    val duracaoMin: Int,
    val local: String
)

/** Erro com mensagem pronta para mostrar na tela. */
class ErroAmigo(mensagem: String) : Exception(mensagem)

// Sem 0, O, 1, I e L, que se confundem quando alguém digita o código
private const val LETRAS_CODIGO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
private const val TAMANHO_CODIGO = 8

fun gerarCodigoAmigo(sorteio: Random = Random.Default): String =
    (1..TAMANHO_CODIGO).map { LETRAS_CODIGO[sorteio.nextInt(LETRAS_CODIGO.length)] }.joinToString("")

/** Aceita o código com traço, espaço ou letra minúscula, como vem colado de uma mensagem. */
fun limparCodigo(texto: String): String = texto.uppercase().filter { it in LETRAS_CODIGO }

fun codigoValido(codigo: String): Boolean = codigo.length == TAMANHO_CODIGO

/** "ABCD-2345": mais fácil de ler e de ditar. */
fun codigoParaMostrar(codigo: String): String = codigo.chunked(TAMANHO_CODIGO / 2).joinToString("-")

fun mensagemCodigo(codigo: String): String =
    "Vamos combinar nossas folgas no Folgui? Meu código de amigo é ${codigoParaMostrar(codigo)}"

/**
 * O que o amigo vê: só a escala, sem eventos nem sono. Atestado vira folga comum
 * e o nome do outro emprego ou da faculdade não sai do aparelho.
 */
fun escalaParaAmigo(voce: Pessoa): String {
    val semMotivo = voce.excecoes.mapValues { (_, e) ->
        if (e.tipo == TipoExcecao.AUSENCIA) e.copy(tipo = TipoExcecao.FOLGA) else e
    }
    return JSONObject()
        .put("escala", voce.escala.codigo)
        .put("inicio", diaParaTexto(voce.inicioCiclo))
        .put("padrao", padraoParaTexto(voce.padrao))
        .put("feriasInicio", voce.feriasInicio?.let(::diaParaTexto) ?: "")
        .put("feriasFim", voce.feriasFim?.let(::diaParaTexto) ?: "")
        .put("descanso", voce.descansoMin)
        .put("excecoes", excecoesParaJson(semMotivo))
        .put("atividades", atividadesParaTexto(voce.atividades.map { it.copy(nome = it.tipo.rotulo) }))
        .toString()
}

/** Retorna null se o texto estiver corrompido: a escala antiga do amigo fica como está. */
fun escalaDoAmigo(texto: String, nome: String, uid: String): Pessoa? = try {
    val o = JSONObject(texto)
    Pessoa(
        id = 0,
        nome = nome,
        escala = TipoEscala.porCodigo(o.getString("escala")),
        inicioCiclo = textoParaDia(o.getString("inicio")) ?: error("início inválido"),
        padrao = textoParaPadrao(o.getString("padrao")) ?: error("ciclo inválido"),
        feriasInicio = textoParaDia(o.getString("feriasInicio")),
        feriasFim = textoParaDia(o.getString("feriasFim")),
        descansoMin = o.getInt("descanso"),
        excecoes = jsonParaExcecoes(o.getJSONArray("excecoes")),
        atividades = textoParaAtividades(o.optString("atividades")),
        amigoUid = uid
    )
} catch (e: Exception) {
    null
}

/** O que precisa mudar no aparelho para ficar igual às contas dos amigos. */
data class MudancasAmigos(val novos: List<Pessoa>, val alterados: List<Pessoa>, val removidos: List<Pessoa>)

/**
 * [uids] são as amizades que existem na nuvem e [remotos], as escalas que deu para ler.
 * Quem está em [uids] sem escala legível continua como está, em vez de sumir da lista.
 * Pessoas cadastradas à mão nunca são tocadas.
 */
fun mudancasAmigos(locais: List<Pessoa>, uids: Set<String>, remotos: List<Pessoa>): MudancasAmigos {
    val amigosLocais = locais.filter { it.amigoUid.isNotEmpty() }
    val porUid = amigosLocais.associateBy { it.amigoUid }
    val novos = remotos.filter { it.amigoUid !in porUid }
    val alterados = remotos.mapNotNull { remoto ->
        val local = porUid[remoto.amigoUid] ?: return@mapNotNull null
        remoto.copy(id = local.id).takeIf { it != local }
    }
    return MudancasAmigos(novos, alterados, amigosLocais.filter { it.amigoUid !in uids })
}

/** Amigos que devem receber o convite: só quem foi escolhido de propósito para o evento. */
fun amigosDoEvento(evento: Evento, pessoas: List<Pessoa>): List<Pessoa> =
    pessoas.filter { it.amigoUid.isNotEmpty() && it.id in evento.participantes.orEmpty() }

/** Evento criado a partir do convite aceito, com quem convidou entre os participantes. */
fun eventoDoConvite(convite: Convite, pessoas: List<Pessoa>) = Evento(
    titulo = convite.titulo,
    dia = convite.dia,
    horario = convite.horario,
    local = convite.local,
    antecedenciaMin = 60,
    duracaoMin = convite.duracaoMin,
    participantes = pessoas.filter { it.amigoUid == convite.deUid }.map { it.id }.toSet()
)

/** Situação depois de sincronizar, para a tela mostrar. */
data class SituacaoAmigos(val codigo: String, val convites: List<Convite>, val mudou: Boolean)

class AmigosRepository {

    private val banco get() = FirebaseFirestore.getInstance()
    private val meuUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: error("Sem conta")

    private fun perfil(uid: String) = banco.collection("perfis").document(uid)

    /** Publica a escala atual e devolve o código desta conta (na primeira vez, cria um). */
    private suspend fun publicar(voce: Pessoa): String {
        val codigo = perfil(meuUid).get().await().getString("codigo") ?: criarCodigo()
        perfil(meuUid).set(
            mapOf("nome" to voce.nome, "codigo" to codigo, "escala" to escalaParaAmigo(voce)),
            SetOptions.merge()
        ).await()
        return codigo
    }

    private suspend fun criarCodigo(): String {
        repeat(5) {
            val codigo = gerarCodigoAmigo()
            val documento = banco.collection("codigos").document(codigo)
            if (!documento.get().await().exists()) {
                documento.set(mapOf("uid" to meuUid)).await()
                return codigo
            }
        }
        throw ErroAmigo("Não consegui criar seu código. Tente de novo.")
    }

    /** Conecta as duas contas: cada uma passa a enxergar a escala da outra. */
    suspend fun adicionar(codigo: String) {
        val uid = banco.collection("codigos").document(codigo).get().await().getString("uid")
            ?: throw ErroAmigo("Não achei esse código.")
        if (uid == meuUid) throw ErroAmigo("Esse é o seu próprio código.")
        val prova = mapOf("codigo" to codigo)
        perfil(uid).collection("amigos").document(meuUid).set(prova).await()
        perfil(meuUid).collection("amigos").document(uid).set(prova).await()
    }

    suspend fun remover(uid: String) {
        perfil(uid).collection("amigos").document(meuUid).delete().await()
        perfil(meuUid).collection("amigos").document(uid).delete().await()
    }

    private suspend fun escalaDe(uid: String): Pessoa? = try {
        val doc = perfil(uid).get().await()
        escalaDoAmigo(doc.getString("escala").orEmpty(), doc.getString("nome").orEmpty(), uid)
    } catch (e: Exception) {
        // O amigo pode ter acabado de desfazer a amizade
        null
    }

    /** Publica a escala, traz a dos amigos para o SQLite e busca os convites. */
    suspend fun sincronizar(voce: Pessoa, repository: FolguiRepository): SituacaoAmigos {
        val codigo = publicar(voce)
        val uids = perfil(meuUid).collection("amigos").get().await().documents.map { it.id }.toSet()
        val remotos = uids.mapNotNull { escalaDe(it) }
        val mudancas = mudancasAmigos(repository.listarPessoas(), uids, remotos)

        mudancas.novos.forEach { amigo ->
            val id = repository.inserirPessoa(amigo)
            repository.substituirExcecoes(id, amigo.excecoes)
        }
        mudancas.alterados.forEach { amigo ->
            repository.atualizarPessoa(amigo)
            repository.substituirExcecoes(amigo.id, amigo.excecoes)
        }
        mudancas.removidos.forEach { repository.excluirPessoa(it.id) }

        val mudou = mudancas.novos.isNotEmpty() || mudancas.alterados.isNotEmpty() || mudancas.removidos.isNotEmpty()
        return SituacaoAmigos(codigo, listarConvites(), mudou)
    }

    private suspend fun listarConvites(): List<Convite> =
        perfil(meuUid).collection("convites").get().await().documents.mapNotNull { d ->
            Convite(
                id = d.id,
                deUid = d.getString("de") ?: return@mapNotNull null,
                deNome = d.getString("nome").orEmpty(),
                titulo = d.getString("titulo") ?: return@mapNotNull null,
                dia = textoParaDia(d.getString("dia").orEmpty()) ?: return@mapNotNull null,
                horario = d.getString("horario") ?: return@mapNotNull null,
                duracaoMin = d.getLong("duracao")?.toInt() ?: SEM_FIM,
                local = d.getString("local").orEmpty()
            )
        }.filter { it.dia >= hoje() }.sortedBy { it.dia }

    suspend fun enviarConvite(amigo: Pessoa, evento: Evento, meuNome: String) {
        perfil(amigo.amigoUid).collection("convites").add(
            mapOf(
                "de" to meuUid,
                "nome" to meuNome,
                "titulo" to evento.titulo,
                "dia" to diaParaTexto(evento.dia),
                "horario" to evento.horario,
                "duracao" to evento.duracaoMin,
                "local" to evento.local
            )
        ).await()
    }

    suspend fun apagarConvite(id: String) {
        perfil(meuUid).collection("convites").document(id).delete().await()
    }
}
