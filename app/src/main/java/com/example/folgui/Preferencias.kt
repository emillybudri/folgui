package com.example.folgui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// ============================================================================
// Persistência de Preferências com DataStore: a escala do próprio usuário
// ============================================================================
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias")

private val CHAVE_NOME = stringPreferencesKey("nome")
private val CHAVE_ESCALA = stringPreferencesKey("escala")
private val CHAVE_INICIO = stringPreferencesKey("inicio_ciclo")
private val CHAVE_FERIAS_INICIO = stringPreferencesKey("ferias_inicio")
private val CHAVE_FERIAS_FIM = stringPreferencesKey("ferias_fim")
val CHAVE_AGENDA = stringPreferencesKey("google_agenda")
private val CHAVE_DIAS_SEMANA = stringPreferencesKey("dias_semana")
private val CHAVE_TURNO_INICIO = stringPreferencesKey("turno_inicio")
private val CHAVE_TURNO_FIM = stringPreferencesKey("turno_fim")
private val CHAVE_PADRAO = stringPreferencesKey("padrao")
private val CHAVE_DESCANSO = stringPreferencesKey("descanso_min")
private val CHAVE_SONO = stringPreferencesKey("sono")
val CHAVE_VISAO = stringPreferencesKey("visao_calendario")
val CHAVE_NUVEM_EM = stringPreferencesKey("nuvem_atualizada_em")
private val CHAVE_ATIVIDADES = stringPreferencesKey("atividades")
private val CHAVE_FERIADOS = stringPreferencesKey("folga_feriados")
val CHAVE_MODO_FACIL = stringPreferencesKey("modo_facil")

data class Perfil(
    val nome: String,
    val escala: TipoEscala,
    val inicioCiclo: Int,
    val feriasInicio: Int?,
    val feriasFim: Int?,
    val padrao: List<Turno?>,
    val descansoMin: Int,
    val sono: Turno?,
    val atividades: List<Atividade> = emptyList(),
    val folgaFeriados: Boolean = true
) {
    fun comoPessoa(excecoes: Map<Int, Excecao>) = Pessoa(
        id = Pessoa.ID_VOCE,
        nome = nome,
        escala = escala,
        inicioCiclo = inicioCiclo,
        padrao = padrao,
        feriasInicio = feriasInicio,
        feriasFim = feriasFim,
        descansoMin = descansoMin,
        excecoes = excecoes,
        sono = sono,
        atividades = atividades,
        folgaFeriados = folgaFeriados
    )
}

/** Envolve o perfil para diferenciar "carregando" (null) de "ainda não configurado" (perfil null). */
data class EstadoPerfil(
    val perfil: Perfil?,
    val agendaConectada: Boolean,
    /** A pessoa negou o acesso ou desconectou: o Folgui não pede de novo nem grava na agenda. */
    val agendaDesligada: Boolean,
    val visao: VisaoCalendario,
    /** Letras maiores no app inteiro. */
    val modoFacil: Boolean
)

private fun lerPerfil(prefs: Preferences): Perfil? {
    val nome = prefs[CHAVE_NOME] ?: return null
    val inicio = textoParaDia(prefs[CHAVE_INICIO] ?: "") ?: return null
    val escala = TipoEscala.porCodigo(prefs[CHAVE_ESCALA])
    // Perfis antigos com escala 5x2 viram "dias fixos" com os mesmos dias da semana
    val diasSemana = prefs[CHAVE_DIAS_SEMANA]?.toIntOrNull()
        ?: if (prefs[CHAVE_ESCALA] == "5x2") diasSemanaDe5x2(inicio) else SEG_A_SEX
    return Perfil(
        nome = nome,
        escala = escala,
        inicioCiclo = inicio,
        feriasInicio = textoParaDia(prefs[CHAVE_FERIAS_INICIO] ?: ""),
        feriasFim = textoParaDia(prefs[CHAVE_FERIAS_FIM] ?: ""),
        // Perfis da versão anterior guardavam só um turno: monta o ciclo a partir dele
        padrao = textoParaPadrao(prefs[CHAVE_PADRAO] ?: "") ?: padraoLegado(
            escala,
            diasSemana,
            Turno(
                prefs[CHAVE_TURNO_INICIO]?.toIntOrNull() ?: escala.turnoPadrao.inicio,
                prefs[CHAVE_TURNO_FIM]?.toIntOrNull() ?: escala.turnoPadrao.fim
            )
        ),
        descansoMin = prefs[CHAVE_DESCANSO]?.toIntOrNull() ?: 0,
        sono = Turno.deCodigo(prefs[CHAVE_SONO] ?: ""),
        atividades = textoParaAtividades(prefs[CHAVE_ATIVIDADES] ?: "")
    )
}

/** Lê tudo que o app precisa das preferências de uma vez. */
fun lerEstado(prefs: Preferences) = EstadoPerfil(
    perfil = lerPerfil(prefs),
    agendaConectada = prefs[CHAVE_AGENDA] == "1",
    agendaDesligada = prefs[CHAVE_AGENDA] == "0",
    visao = VisaoCalendario.porNome(prefs[CHAVE_VISAO]),
    modoFacil = prefs[CHAVE_MODO_FACIL] == "1"
)

/** Grava a escala do usuário nas preferências. */
suspend fun Context.salvarPerfilNasPreferencias(pessoa: Pessoa) {
    val turnoPrincipal = pessoa.turnos.firstOrNull() ?: pessoa.escala.turnoPadrao
    dataStore.edit { prefs ->
        prefs[CHAVE_NOME] = pessoa.nome
        prefs[CHAVE_ESCALA] = pessoa.escala.codigo
        prefs[CHAVE_INICIO] = diaParaTexto(pessoa.inicioCiclo)
        prefs[CHAVE_FERIAS_INICIO] = pessoa.feriasInicio?.let(::diaParaTexto) ?: ""
        prefs[CHAVE_FERIAS_FIM] = pessoa.feriasFim?.let(::diaParaTexto) ?: ""
        prefs[CHAVE_DIAS_SEMANA] = pessoa.diasSemana.toString()
        prefs[CHAVE_TURNO_INICIO] = turnoPrincipal.inicio.toString()
        prefs[CHAVE_TURNO_FIM] = turnoPrincipal.fim.toString()
        prefs[CHAVE_PADRAO] = padraoParaTexto(pessoa.padrao)
        prefs[CHAVE_DESCANSO] = pessoa.descansoMin.toString()
        prefs[CHAVE_SONO] = pessoa.sono?.codigo ?: ""
        prefs[CHAVE_ATIVIDADES] = atividadesParaTexto(pessoa.atividades)
        prefs[CHAVE_FERIADOS] = if (pessoa.folgaFeriados) "sim" else "nao"
    }
}
