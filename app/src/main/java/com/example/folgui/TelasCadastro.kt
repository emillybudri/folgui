package com.example.folgui

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

// ============================================================================
// CADASTRO DA ESCALA EM ETAPAS (o usuário no DataStore ou outra pessoa no SQLite)
// Quem → Rotina → Horário → Dias → Confirmar
// ============================================================================
enum class ModoEscala { CONFIGURACAO, PERFIL, NOVA_PESSOA, EDITAR_PESSOA, ATIVIDADE }

class Etapa(val nome: String, val icone: ImageVector)

val ETAPAS = listOf(
    Etapa("Quem", Icones.Pessoas),
    Etapa("Rotina", Icones.Trabalho),
    Etapa("Horário", Icones.Relogio),
    Etapa("Dias", Icones.Calendario),
    Etapa("Confirmar", Icones.Confirmar)
)

const val SEM_DATA = -1

// Letras do ciclo: A = horário principal, B = segundo horário, F = folga
const val FOLGA = 'F'
const val TURNO_A = 'A'
const val TURNO_B = 'B'

/** Primeiro dia da grade de escolha: domingo da semana passada. */
fun inicioDaGrade(): Int = hoje() - diaDaSemana(hoje()) - 7

/** Traz o início do ciclo para perto de hoje (mesma escala, dia visível na grade). */
fun aproximarInicio(inicio: Int, ciclo: Int): Int =
    inicio + ciclo * Math.floorDiv(hoje() - inicio, ciclo)

/** Escala de revezamento: o candidato a início do ciclo bate com as respostas "trabalha?" */
fun combina(escala: TipoEscala, inicio: Int, respostas: String): Boolean {
    val ciclo = escala.diasTrabalho + escala.diasFolga
    return respostas.indices.all { i ->
        (Math.floorMod(hoje() + i - inicio, ciclo) < escala.diasTrabalho) == (respostas[i] == 'S')
    }
}

fun candidatos(escala: TipoEscala, respostas: String): List<Int> {
    val ciclo = escala.diasTrabalho + escala.diasFolga
    return (0 until ciclo).map { hoje() - it }.filter { combina(escala, it, respostas) }
}

@Composable
fun TelaFormularioEscala(
    modo: ModoEscala,
    inicial: Pessoa?,
    cidadeInicial: String?,
    onVoltar: () -> Unit,
    onSalvar: (Pessoa, String) -> Unit,
    onExcluir: (() -> Unit)? = null,
    /** Aparece embaixo do nome, na primeira etapa. */
    extraInicio: (@Composable () -> Unit)? = null,
    /** Só no modo [ModoEscala.ATIVIDADE]: emprego ou faculdade. */
    tipoAtividade: TipoAtividade? = null
) {
    val context = LocalContext.current
    val ehUsuario = modo == ModoEscala.CONFIGURACAO || modo == ModoEscala.PERFIL
    val editando = inicial != null
    val escalaInicial = inicial?.escala ?: TipoEscala.SEMANAL
    val turnosIniciais = inicial?.turnos.orEmpty()
    val turnoAInicial = turnosIniciais.getOrNull(0) ?: tipoAtividade?.turnoInicial ?: escalaInicial.turnoPadrao
    val turnoBInicial = turnosIniciais.getOrNull(1)

    fun codificar(padrao: List<Turno?>): String =
        padrao.joinToString("") { t -> if (t == null) "F" else if (t == turnoAInicial) "A" else "B" }

    var etapa by rememberSaveable { mutableIntStateOf(0) }
    var etapaMaxima by rememberSaveable { mutableIntStateOf(if (editando) ETAPAS.lastIndex else 0) }

    var nome by rememberSaveable { mutableStateOf(inicial?.nome ?: "") }
    // Outras atividades: ficam aqui até salvar, porque no primeiro cadastro ainda não há perfil
    var atividadesTexto by rememberSaveable { mutableStateOf(atividadesParaTexto(inicial?.atividades.orEmpty())) }
    val atividades = textoParaAtividades(atividadesTexto)
    var novaAtividade by rememberSaveable { mutableStateOf<String?>(null) }
    var editandoAtividade by rememberSaveable { mutableIntStateOf(-1) }
    var email by rememberSaveable { mutableStateOf(inicial?.email ?: "") }
    var cidade by rememberSaveable { mutableStateOf(cidadeInicial ?: "") }
    var codigoEscala by rememberSaveable { mutableStateOf(escalaInicial.codigo) }

    var aInicio by rememberSaveable { mutableIntStateOf(turnoAInicial.inicio) }
    var aFim by rememberSaveable { mutableIntStateOf(turnoAInicial.fim) }
    var temTurnoB by rememberSaveable { mutableStateOf(turnoBInicial != null) }
    var bInicio by rememberSaveable { mutableIntStateOf(turnoBInicial?.inicio ?: 19 * 60) }
    var bFim by rememberSaveable { mutableIntStateOf(turnoBInicial?.fim ?: 7 * 60) }
    var descanso by rememberSaveable { mutableIntStateOf(inicial?.descansoMin ?: 0) }

    var diasSemanal by rememberSaveable {
        mutableStateOf(if (escalaInicial == TipoEscala.SEMANAL && inicial != null) codificar(inicial.padrao) else "FAAAAAF")
    }
    var diasCiclo by rememberSaveable {
        mutableStateOf(if (escalaInicial == TipoEscala.PERSONALIZADA && inicial != null) codificar(inicial.padrao) else "AAFF")
    }
    var inicio by rememberSaveable {
        mutableIntStateOf(
            if (inicial != null && escalaInicial != TipoEscala.SEMANAL) {
                aproximarInicio(inicial.inicioCiclo, inicial.padrao.size)
            } else {
                hoje()
            }
        )
    }
    var pincel by rememberSaveable { mutableStateOf(TURNO_A) }

    // Revezamento: perguntas "trabalha hoje?" ou toque no calendário
    var respostas by rememberSaveable { mutableStateOf("") }
    var cicloDefinido by rememberSaveable { mutableStateOf(editando && escalaInicial.revezamento) }
    var marcarNoCalendario by rememberSaveable { mutableStateOf(editando) }

    var temFerias by rememberSaveable { mutableStateOf(inicial?.temFerias == true) }
    var feriasInicio by rememberSaveable { mutableIntStateOf(inicial?.feriasInicio ?: SEM_DATA) }
    var feriasFim by rememberSaveable { mutableIntStateOf(inicial?.feriasFim ?: SEM_DATA) }
    var temSono by rememberSaveable { mutableStateOf(inicial?.sono != null) }
    var dormir by rememberSaveable { mutableIntStateOf((inicial?.sono ?: SONO_PADRAO).inicio) }
    var acordar by rememberSaveable { mutableIntStateOf((inicial?.sono ?: SONO_PADRAO).fim) }
    var tentouAvancar by rememberSaveable { mutableStateOf(false) }
    var confirmarExclusao by rememberSaveable { mutableStateOf(false) }

    val escala = TipoEscala.porCodigo(codigoEscala)
    val turnoA = Turno(aInicio, aFim)
    val turnoB = Turno(bInicio, bFim)
    val usaDoisHorarios = temTurnoB && !escala.revezamento

    fun decodificar(dias: String): List<Turno?> = dias.map { c ->
        when (c) {
            TURNO_A -> turnoA
            TURNO_B -> if (usaDoisHorarios) turnoB else turnoA
            else -> null
        }
    }

    val padrao = when (escala) {
        TipoEscala.SEMANAL -> decodificar(diasSemanal)
        TipoEscala.PERSONALIZADA -> decodificar(diasCiclo)
        else -> escala.padraoCom(turnoA)
    }

    val previa = Pessoa(
        id = inicial?.id ?: 0,
        nome = nome.trim(),
        escala = escala,
        inicioCiclo = if (escala == TipoEscala.SEMANAL) hoje() else inicio,
        padrao = padrao,
        feriasInicio = feriasInicio.takeIf { temFerias && it != SEM_DATA && feriasFim != SEM_DATA && feriasFim >= it },
        feriasFim = feriasFim.takeIf { temFerias && feriasInicio != SEM_DATA && it != SEM_DATA && it >= feriasInicio },
        email = email.trim(),
        descansoMin = if (padrao.filterNotNull().any { it.noturno }) descanso else 0,
        excecoes = inicial?.excecoes.orEmpty(),
        sono = if (temSono) Turno(dormir, acordar) else null,
        atividades = atividades
    )
    val sujeito = if (ehUsuario) "Você" else nome.trim().ifEmpty { "A pessoa" }

    // Validação por etapa (os erros aparecem só depois de tentar avançar)
    val erroNome = when {
        !nome.isBlank() -> null
        ehUsuario -> "Informe seu nome"
        modo == ModoEscala.ATIVIDADE -> "Dê um nome"
        else -> "Informe o nome da pessoa"
    }
    val erroEmail = "E-mail inválido".takeIf {
        email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }
    val erroDias = when {
        escala.revezamento && !cicloDefinido ->
            "Toque em um dia de trabalho"
        escala == TipoEscala.PERSONALIZADA && diasCiclo.none { it != FOLGA } ->
            "Pinte um dia de trabalho"
        else -> null
    }
    val erroFeriasInicio = "Escolha o início".takeIf { temFerias && feriasInicio == SEM_DATA }
    val erroFeriasFim = when {
        !temFerias -> null
        feriasFim == SEM_DATA -> "Escolha o fim"
        feriasInicio != SEM_DATA && feriasFim < feriasInicio -> "Deve ser depois do início"
        else -> null
    }

    fun etapaValida(n: Int): Boolean = when (n) {
        0 -> erroNome == null && erroEmail == null
        3 -> erroDias == null
        4 -> erroFeriasInicio == null && erroFeriasFim == null
        else -> true
    }

    fun erroVisivel(erro: String?) = erro.takeIf { tentouAvancar }

    fun irPara(n: Int) {
        tentouAvancar = false
        etapa = n
        etapaMaxima = maxOf(etapaMaxima, n)
    }

    fun avancar() {
        if (!etapaValida(etapa)) {
            tentouAvancar = true
            return
        }
        if (etapa < ETAPAS.lastIndex) {
            irPara(etapa + 1)
        } else {
            // Confere todas as etapas antes de salvar (na edição dá para pular direto)
            val invalida = ETAPAS.indices.firstOrNull { !etapaValida(it) }
            if (invalida != null) {
                etapa = invalida
                tentouAvancar = true
            } else {
                onSalvar(previa, cidade.trim())
            }
        }
    }

    fun voltar() {
        if (etapa == 0) onVoltar() else irPara(etapa - 1)
    }

    BackHandler(enabled = etapa > 0 || modo == ModoEscala.ATIVIDADE) { voltar() }

    fun escolherEscala(nova: TipoEscala) {
        if (nova == escala) return
        // Se o horário ainda é o padrão da escala anterior, troca pelo padrão da nova
        if (turnoA == escala.turnoPadrao) {
            aInicio = nova.turnoPadrao.inicio
            aFim = nova.turnoPadrao.fim
        }
        codigoEscala = nova.codigo
        respostas = ""
        cicloDefinido = false
        marcarNoCalendario = false
    }

    // Emprego ou faculdade usam estas mesmas etapas, por cima do cadastro aberto
    val atividadeAberta = atividades.getOrNull(editandoAtividade)
    val tipoAberto = atividadeAberta?.tipo ?: TipoAtividade.porCodigo(novaAtividade)
    if (tipoAberto != null) {
        fun fechar() {
            novaAtividade = null
            editandoAtividade = -1
        }
        TelaFormularioEscala(
            modo = ModoEscala.ATIVIDADE,
            inicial = atividadeAberta?.comoPessoa(),
            cidadeInicial = null,
            tipoAtividade = tipoAberto,
            onVoltar = ::fechar,
            onSalvar = { p, _ ->
                val nova = Atividade(tipoAberto, p.nome, p.escala, p.inicioCiclo, p.padrao)
                val lista = if (atividadeAberta == null) {
                    atividades + nova
                } else {
                    atividades.mapIndexed { i, a -> if (i == editandoAtividade) nova else a }
                }
                atividadesTexto = atividadesParaTexto(lista)
                fechar()
            },
            onExcluir = atividadeAberta?.let {
                {
                    atividadesTexto = atividadesParaTexto(atividades.filterIndexed { i, _ -> i != editandoAtividade })
                    fechar()
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = tituloFormulario(modo, inicial, tipoAtividade),
                onVoltar = onVoltar,
                iconeVoltar = if (modo == ModoEscala.CONFIGURACAO) Icones.Voltar else Icones.Fechar
            )
        },
        bottomBar = {
            BotoesEtapa(etapa = etapa, modo = modo, onVoltar = ::voltar, onAvancar = ::avancar)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            IndicadorEtapas(
                atual = etapa,
                alcancada = etapaMaxima,
                onIr = { if (it <= etapaMaxima) irPara(it) }
            )

            val rolagem = rememberScrollState()
            LaunchedEffect(etapa) { rolagem.scrollTo(0) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rolagem),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .limitarLargura()
                        .padding(horizontal = Espaco.lg, vertical = Espaco.md),
                    verticalArrangement = Arrangement.spacedBy(Espaco.xl)
                ) {
                    when (etapa) {
                        0 -> EtapaQuem(
                            modo = modo,
                            nome = nome,
                            email = email,
                            erroNome = erroVisivel(erroNome),
                            erroEmail = erroVisivel(erroEmail),
                            tipoAtividade = tipoAtividade,
                            onNome = { nome = it },
                            onEmail = { email = it },
                            onConcluir = ::avancar,
                            extraInicio = extraInicio
                        )

                        1 -> EtapaRotina(
                            titulo = if (ehUsuario) "Sua rotina" else "Rotina de $sujeito",
                            escala = escala,
                            onEscolher = ::escolherEscala
                        )

                        2 -> EtapaHorario(
                            escala = escala,
                            temTurnoB = temTurnoB,
                            turnoA = turnoA,
                            turnoB = turnoB,
                            descanso = descanso,
                            onTurnoA = { aInicio = it.inicio; aFim = it.fim },
                            onTurnoB = { bInicio = it.inicio; bFim = it.fim },
                            onAdicionarTurnoB = { temTurnoB = true },
                            onRemoverTurnoB = {
                                temTurnoB = false
                                diasSemanal = diasSemanal.replace(TURNO_B, TURNO_A)
                                diasCiclo = diasCiclo.replace(TURNO_B, TURNO_A)
                                pincel = TURNO_A
                            },
                            onDescanso = { descanso = it }
                        )

                        3 -> {
                            when {
                                escala == TipoEscala.SEMANAL -> EtapaDiasSemana(
                                    dias = diasSemanal,
                                    diasMarcados = previa.diasSemana,
                                    usaDoisHorarios = usaDoisHorarios,
                                    pincel = pincel,
                                    turnoA = turnoA,
                                    turnoB = turnoB,
                                    onPincel = { pincel = it },
                                    onTocar = { d ->
                                        diasSemanal = pintar(diasSemanal, d, if (usaDoisHorarios) pincel else TURNO_A)
                                    }
                                )

                                escala.revezamento -> EtapaDiasRevezamento(
                                    escala = escala,
                                    sujeito = sujeito,
                                    noturno = turnoA.noturno,
                                    previa = previa,
                                    inicio = inicio,
                                    respostas = respostas,
                                    cicloDefinido = cicloDefinido,
                                    marcarNoCalendario = marcarNoCalendario,
                                    onMudarInicio = {
                                        inicio = it
                                        cicloDefinido = true
                                    },
                                    onUsarPerguntas = {
                                        marcarNoCalendario = false
                                        respostas = ""
                                        cicloDefinido = false
                                    },
                                    onUsarCalendario = { marcarNoCalendario = true },
                                    onResponder = { trabalha ->
                                        val novas = respostas + if (trabalha) "S" else "N"
                                        respostas = novas
                                        val achados = candidatos(escala, novas)
                                        if (achados.size == 1) {
                                            inicio = achados[0]
                                            cicloDefinido = true
                                        }
                                    },
                                    onRecomecar = {
                                        respostas = ""
                                        cicloDefinido = false
                                    }
                                )

                                else -> {
                                    TituloEtapa("Monte o ciclo", "Pinte os dias")
                                    EditorCiclo(
                                        dias = diasCiclo,
                                        inicio = inicio,
                                        turnoA = turnoA,
                                        turnoB = turnoB.takeIf { usaDoisHorarios },
                                        pincel = pincel,
                                        context = context,
                                        onPincel = { pincel = it },
                                        onDias = { diasCiclo = it },
                                        onInicio = { inicio = it },
                                        onModelo = { m ->
                                            diasCiclo = m.dias
                                            aInicio = m.turnoA.inicio
                                            aFim = m.turnoA.fim
                                            temTurnoB = m.turnoB != null
                                            m.turnoB?.let { bInicio = it.inicio; bFim = it.fim }
                                            pincel = TURNO_A
                                        }
                                    )
                                }
                            }
                            erroVisivel(erroDias)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        else -> {
                            TituloEtapa("Confira", imagem = R.drawable.ilustracao_pronto)
                            CartaoResumoEscala(
                                previa = previa,
                                idDaCor = inicial?.id ?: if (ehUsuario) Pessoa.ID_VOCE else 0,
                                onCorrigirDias = { irPara(3) }
                            )

                            if (modo != ModoEscala.ATIVIDADE) {
                                Secao("Férias") {
                                    CampoFerias(
                                        temFerias = temFerias,
                                        inicio = feriasInicio,
                                        fim = feriasFim,
                                        erroInicio = erroVisivel(erroFeriasInicio),
                                        erroFim = erroVisivel(erroFeriasFim),
                                        context = context,
                                        onAtivar = {
                                            temFerias = it
                                            feriasInicio = SEM_DATA
                                            feriasFim = SEM_DATA
                                        },
                                        onInicio = { escolhido ->
                                            feriasInicio = escolhido
                                            if (feriasFim != SEM_DATA && feriasFim < escolhido) feriasFim = SEM_DATA
                                        },
                                        onFim = { feriasFim = it }
                                    )
                                }
                                SecaoAtividades(
                                    atividades = atividades,
                                    onAbrir = { editandoAtividade = it },
                                    onNova = { novaAtividade = it.codigo }
                                )
                            }

                            if (ehUsuario) {
                                SecaoSono(
                                    temSono = temSono,
                                    dormir = dormir,
                                    acordar = acordar,
                                    onTemSono = { temSono = it },
                                    onDormir = { dormir = it },
                                    onAcordar = { acordar = it }
                                )
                                SecaoCidade(cidade = cidade, onCidade = { cidade = it })
                            }

                            if (onExcluir != null) {
                                BotaoRemover(
                                    nome = inicial?.nome ?: "pessoa",
                                    onClick = { confirmarExclusao = true }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Espaco.lg))
                }
            }
        }
    }

    if (confirmarExclusao && onExcluir != null) {
        DialogoRemover(
            nome = inicial?.nome ?: "pessoa",
            modo = modo,
            onConfirmar = {
                confirmarExclusao = false
                onExcluir()
            },
            onFechar = { confirmarExclusao = false }
        )
    }
}
