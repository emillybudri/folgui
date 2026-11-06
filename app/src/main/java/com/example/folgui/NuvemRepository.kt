package com.example.folgui

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

data class ContaNuvem(val uid: String, val email: String)

/** O que está guardado na conta, com o momento em que foi enviado. */
data class CopiaNuvem(val dados: String, val atualizadoEm: Long)

/** Tudo que precisa voltar em outro aparelho. */
data class DadosNuvem(
    val perfil: Perfil,
    val excecoesVoce: Map<Int, Excecao>,
    val pessoas: List<Pessoa>,
    val eventos: List<Evento>
)

// ---------- Conversão para texto (o id da Google Agenda fica de fora: ele só vale em cada aparelho) ----------

private fun excecoesParaJson(excecoes: Map<Int, Excecao>) = JSONArray().apply {
    excecoes.values.sortedBy { it.dia }.forEach { e ->
        put(
            JSONObject()
                .put("dia", diaParaTexto(e.dia))
                .put("tipo", e.tipo.codigo)
                .put("turno", e.turno?.codigo ?: "")
        )
    }
}

private fun jsonParaExcecoes(lista: JSONArray): Map<Int, Excecao> {
    val mapa = mutableMapOf<Int, Excecao>()
    for (i in 0 until lista.length()) {
        val o = lista.getJSONObject(i)
        val dia = textoParaDia(o.getString("dia")) ?: continue
        val tipo = TipoExcecao.porCodigo(o.getString("tipo")) ?: continue
        val turno = Turno.deCodigo(o.getString("turno"))
        if (tipo.trabalha && turno == null) continue
        mapa[dia] = Excecao(dia, tipo, turno.takeIf { tipo.trabalha })
    }
    return mapa
}

/** Texto vazio quando não há atividades, para combinar com o valor padrão do banco. */
fun atividadesParaTexto(atividades: List<Atividade>): String {
    if (atividades.isEmpty()) return ""
    return JSONArray().apply {
        atividades.forEach { a ->
            put(
                JSONObject()
                    .put("tipo", a.tipo.codigo)
                    .put("nome", a.nome)
                    .put("escala", a.escala.codigo)
                    .put("inicio", diaParaTexto(a.inicioCiclo))
                    .put("padrao", padraoParaTexto(a.padrao))
            )
        }
    }.toString()
}

fun textoParaAtividades(texto: String): List<Atividade> {
    if (texto.isBlank()) return emptyList()
    return try {
        val lista = JSONArray(texto)
        (0 until lista.length()).mapNotNull { i ->
            val o = lista.getJSONObject(i)
            Atividade(
                tipo = TipoAtividade.porCodigo(o.getString("tipo")) ?: return@mapNotNull null,
                nome = o.getString("nome"),
                escala = TipoEscala.porCodigo(o.getString("escala")),
                inicioCiclo = textoParaDia(o.getString("inicio")) ?: return@mapNotNull null,
                padrao = textoParaPadrao(o.getString("padrao")) ?: return@mapNotNull null
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun dadosParaTexto(dados: DadosNuvem): String {
    val p = dados.perfil
    val perfil = JSONObject()
        .put("nome", p.nome)
        .put("escala", p.escala.codigo)
        .put("inicio", diaParaTexto(p.inicioCiclo))
        .put("feriasInicio", p.feriasInicio?.let(::diaParaTexto) ?: "")
        .put("feriasFim", p.feriasFim?.let(::diaParaTexto) ?: "")
        .put("cidade", p.cidade)
        .put("padrao", padraoParaTexto(p.padrao))
        .put("descanso", p.descansoMin)
        .put("sono", p.sono?.codigo ?: "")
        .put("atividades", atividadesParaTexto(p.atividades))
    val pessoas = JSONArray().apply {
        dados.pessoas.forEach { q ->
            put(
                JSONObject()
                    .put("id", q.id)
                    .put("nome", q.nome)
                    .put("escala", q.escala.codigo)
                    .put("inicio", diaParaTexto(q.inicioCiclo))
                    .put("feriasInicio", q.feriasInicio?.let(::diaParaTexto) ?: "")
                    .put("feriasFim", q.feriasFim?.let(::diaParaTexto) ?: "")
                    .put("email", q.email)
                    .put("padrao", padraoParaTexto(q.padrao))
                    .put("descanso", q.descansoMin)
                    .put("excecoes", excecoesParaJson(q.excecoes))
                    .put("atividades", atividadesParaTexto(q.atividades))
            )
        }
    }
    val eventos = JSONArray().apply {
        dados.eventos.forEach { e ->
            put(
                JSONObject()
                    .put("id", e.id)
                    .put("titulo", e.titulo)
                    .put("dia", diaParaTexto(e.dia))
                    .put("horario", e.horario)
                    .put("local", e.local)
                    .put("antecedencia", e.antecedenciaMin)
                    .put("duracao", e.duracaoMin)
                    .put("participantes", participantesParaTexto(e.participantes))
            )
        }
    }
    return JSONObject()
        .put("perfil", perfil)
        .put("excecoesVoce", excecoesParaJson(dados.excecoesVoce))
        .put("pessoas", pessoas)
        .put("eventos", eventos)
        .toString()
}

/** Retorna null se o texto estiver corrompido: nesse caso nada do aparelho pode ser substituído. */
fun textoParaDados(texto: String): DadosNuvem? = try {
    val raiz = JSONObject(texto)
    val p = raiz.getJSONObject("perfil")
    val escala = TipoEscala.porCodigo(p.getString("escala"))
    val perfil = Perfil(
        nome = p.getString("nome"),
        escala = escala,
        inicioCiclo = textoParaDia(p.getString("inicio")) ?: error("início inválido"),
        feriasInicio = textoParaDia(p.getString("feriasInicio")),
        feriasFim = textoParaDia(p.getString("feriasFim")),
        cidade = p.getString("cidade"),
        padrao = textoParaPadrao(p.getString("padrao")) ?: error("ciclo inválido"),
        descansoMin = p.getInt("descanso"),
        sono = Turno.deCodigo(p.getString("sono")),
        atividades = textoParaAtividades(p.optString("atividades"))
    )
    val listaPessoas = raiz.getJSONArray("pessoas")
    val pessoas = (0 until listaPessoas.length()).map { i ->
        val o = listaPessoas.getJSONObject(i)
        Pessoa(
            id = o.getInt("id"),
            nome = o.getString("nome"),
            escala = TipoEscala.porCodigo(o.getString("escala")),
            inicioCiclo = textoParaDia(o.getString("inicio")) ?: error("início inválido"),
            padrao = textoParaPadrao(o.getString("padrao")) ?: error("ciclo inválido"),
            feriasInicio = textoParaDia(o.getString("feriasInicio")),
            feriasFim = textoParaDia(o.getString("feriasFim")),
            email = o.getString("email"),
            descansoMin = o.getInt("descanso"),
            excecoes = jsonParaExcecoes(o.getJSONArray("excecoes")),
            atividades = textoParaAtividades(o.optString("atividades"))
        )
    }
    val listaEventos = raiz.getJSONArray("eventos")
    val eventos = (0 until listaEventos.length()).map { i ->
        val o = listaEventos.getJSONObject(i)
        Evento(
            id = o.getInt("id"),
            titulo = o.getString("titulo"),
            dia = textoParaDia(o.getString("dia")) ?: error("dia inválido"),
            horario = o.getString("horario"),
            local = o.getString("local"),
            antecedenciaMin = o.getInt("antecedencia"),
            duracaoMin = o.getInt("duracao"),
            participantes = textoParaParticipantes(o.getString("participantes"))
        )
    }
    DadosNuvem(perfil, jsonParaExcecoes(raiz.getJSONArray("excecoesVoce")), pessoas, eventos)
} catch (e: Exception) {
    null
}

/** Mensagem curta para cada falha de login. */
fun mensagemDeLogin(e: Exception): String = when (e) {
    is FirebaseAuthWeakPasswordException -> "Use 6 caracteres ou mais"
    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "E-mail ou senha incorretos"
    is FirebaseAuthUserCollisionException -> "Este e-mail já tem conta"
    is FirebaseNetworkException -> "Sem internet"
    else -> "Não foi possível entrar"
}

// Login com Google ou e-mail (Firebase Auth) e cópia dos dados em um documento por conta (Firestore)
class NuvemRepository {

    /** Sem o google-services.json no projeto, o app funciona normalmente, só sem conta. */
    val configurada: Boolean = BuildConfig.GOOGLE_WEB_CLIENT.isNotEmpty()

    val contaAtual: ContaNuvem?
        get() = if (configurada) FirebaseAuth.getInstance().currentUser?.let { ContaNuvem(it.uid, it.email.orEmpty()) } else null

    /** Abre a escolha de conta do Google. [context] precisa ser a Activity. */
    suspend fun entrar(context: Context): ContaNuvem {
        val pedido = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT).build())
            .build()
        val resposta = CredentialManager.create(context).getCredential(context, pedido)
        val token = GoogleIdTokenCredential.createFrom(resposta.credential.data).idToken
        return contaDe(
            FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user
        )
    }

    suspend fun entrarComEmail(email: String, senha: String): ContaNuvem =
        contaDe(FirebaseAuth.getInstance().signInWithEmailAndPassword(email, senha).await().user)

    suspend fun criarConta(email: String, senha: String): ContaNuvem =
        contaDe(FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, senha).await().user)

    suspend fun redefinirSenha(email: String) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
    }

    private fun contaDe(usuario: FirebaseUser?): ContaNuvem {
        val conta = usuario ?: error("Login sem usuário")
        return ContaNuvem(conta.uid, conta.email.orEmpty())
    }

    suspend fun sair(context: Context) {
        FirebaseAuth.getInstance().signOut()
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    private fun documento() = FirebaseFirestore.getInstance()
        .collection("usuarios")
        .document(FirebaseAuth.getInstance().currentUser?.uid ?: error("Sem conta"))

    /** Lê direto do servidor: sem internet dá erro, em vez de devolver uma cópia velha. */
    suspend fun baixar(): CopiaNuvem? {
        val doc = documento().get(Source.SERVER).await()
        val dados = doc.getString("dados") ?: return null
        return CopiaNuvem(dados, doc.getLong("atualizadoEm") ?: 0)
    }

    /** Retorna o momento gravado, para comparar com a conta na próxima abertura. */
    suspend fun enviar(dados: String): Long {
        val agora = System.currentTimeMillis()
        documento().set(mapOf("dados" to dados, "atualizadoEm" to agora), SetOptions.merge()).await()
        return agora
    }
}
