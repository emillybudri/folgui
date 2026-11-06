package com.example.folgui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ----------------------------------------------------------------------------
// Partes do formulário de escala: a barra de botões, uma função por etapa e o diálogo
// ----------------------------------------------------------------------------

fun tituloFormulario(modo: ModoEscala, inicial: Pessoa?, tipoAtividade: TipoAtividade?): String = when (modo) {
    ModoEscala.ATIVIDADE -> tipoAtividade?.rotulo ?: "Atividade"
    ModoEscala.CONFIGURACAO -> "Sua escala"
    ModoEscala.PERFIL -> "Minha escala"
    ModoEscala.NOVA_PESSOA -> "Nova pessoa"
    ModoEscala.EDITAR_PESSOA -> "Editar ${inicial?.nome ?: "pessoa"}"
}

/** Botões "Voltar" e "Continuar" no rodapé de todas as etapas. */
@Composable
fun BotoesEtapa(etapa: Int, modo: ModoEscala, onVoltar: () -> Unit, onAvancar: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Espaco.lg, vertical = Espaco.md)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Espaco.md)
        ) {
            if (etapa > 0) {
                OutlinedButton(onClick = onVoltar, modifier = Modifier.heightIn(min = 52.dp)) {
                    Icon(Icones.Anterior, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Espaco.xs))
                    Text("Voltar")
                }
            }
            Button(
                onClick = onAvancar,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    when {
                        etapa < ETAPAS.lastIndex -> "Continuar"
                        modo == ModoEscala.CONFIGURACAO -> "Está certo, concluir"
                        modo == ModoEscala.NOVA_PESSOA -> "Está certo, adicionar"
                        else -> "Salvar alterações"
                    }
                )
            }
        }
    }
}

/** Etapa 1: nome e, para outras pessoas, e-mail. */
@Composable
fun EtapaQuem(
    modo: ModoEscala,
    nome: String,
    email: String,
    erroNome: String?,
    erroEmail: String?,
    tipoAtividade: TipoAtividade?,
    onNome: (String) -> Unit,
    onEmail: (String) -> Unit,
    onConcluir: () -> Unit,
    extraInicio: (@Composable () -> Unit)?
) {
    val ehUsuario = modo == ModoEscala.CONFIGURACAO || modo == ModoEscala.PERFIL
    TituloEtapa(
        when {
            ehUsuario -> "Seu nome"
            modo == ModoEscala.ATIVIDADE -> "Nome"
            else -> "Quem é?"
        },
        imagem = R.drawable.ilustracao_encontro
    )
    CampoTexto(
        valor = nome,
        onValorChange = { onNome(it.take(40)) },
        rotulo = if (ehUsuario) "Seu nome" else "Nome",
        placeholder = if (ehUsuario) null else tipoAtividade?.exemplo ?: "Ex.: Tia Márcia",
        erro = erroNome,
        capitalizacao = KeyboardCapitalization.Words,
        acaoTeclado = if (ehUsuario) ImeAction.Done else ImeAction.Next,
        onConcluir = { if (ehUsuario) onConcluir() },
        modifier = Modifier.fillMaxWidth()
    )
    if (!ehUsuario && modo != ModoEscala.ATIVIDADE) {
        CampoTexto(
            valor = email,
            onValorChange = { onEmail(it.trim().take(80)) },
            rotulo = "E-mail (opcional)",
            placeholder = "nome@gmail.com",
            icone = Icones.Email,
            erro = erroEmail,
            apoio = "Para convites",
            capitalizacao = KeyboardCapitalization.None,
            tipoTeclado = KeyboardType.Email,
            acaoTeclado = ImeAction.Done,
            modifier = Modifier.fillMaxWidth()
        )
    }
    extraInicio?.invoke()
}

/** Etapa 2: o tipo de escala. */
@Composable
fun EtapaRotina(titulo: String, escala: TipoEscala, onEscolher: (TipoEscala) -> Unit) {
    TituloEtapa(titulo, imagem = R.drawable.ilustracao_calendario)
    Column(verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
        CartaoCategoria(
            icone = Icones.Calendario,
            titulo = "Mesmos dias toda semana",
            texto = "Ex.: segunda a sexta",
            selecionado = escala == TipoEscala.SEMANAL,
            onClick = { onEscolher(TipoEscala.SEMANAL) }
        )
        CartaoCategoria(
            icone = Icones.Sincronizar,
            titulo = "Plantão ou revezamento",
            texto = "12x36, 24x48, 2x2, 6x1...",
            selecionado = escala.revezamento,
            onClick = { onEscolher(TipoEscala.ESCALA_12X36) }
        )
        CartaoCategoria(
            icone = Icones.Editar,
            titulo = "Minha escala é diferente",
            texto = "Monte dia a dia",
            selecionado = escala == TipoEscala.PERSONALIZADA,
            onClick = { onEscolher(TipoEscala.PERSONALIZADA) }
        )
    }
    if (escala.revezamento) {
        Secao("Qual escala?") {
            val revezamentos = TipoEscala.entries.filter { it.revezamento }
            Column(verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                revezamentos.chunked(2).forEach { linha ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                        linha.forEach { tipo ->
                            CartaoTipoEscala(
                                tipo = tipo,
                                selecionado = tipo == escala,
                                onClick = { onEscolher(tipo) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (linha.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Etapa 3: um ou dois horários e o descanso depois do turno da noite. */
@Composable
fun EtapaHorario(
    escala: TipoEscala,
    temTurnoB: Boolean,
    turnoA: Turno,
    turnoB: Turno,
    descanso: Int,
    onTurnoA: (Turno) -> Unit,
    onTurnoB: (Turno) -> Unit,
    onAdicionarTurnoB: () -> Unit,
    onRemoverTurnoB: () -> Unit,
    onDescanso: (Int) -> Unit
) {
    val context = LocalContext.current
    val usaDoisHorarios = temTurnoB && !escala.revezamento
    TituloEtapa("Horário", imagem = R.drawable.ilustracao_turnos)
    EditorTurno(
        titulo = if (usaDoisHorarios) "Horário principal" else "Horário",
        turno = turnoA,
        sugestoes = escala.turnosSugeridos,
        context = context,
        onMudar = onTurnoA
    )
    if (!escala.revezamento) {
        if (temTurnoB) {
            EditorTurno(
                titulo = "Segundo horário",
                turno = turnoB,
                sugestoes = listOf(
                    Turno(19 * 60, 7 * 60), Turno(8 * 60, 12 * 60),
                    Turno(14 * 60, 22 * 60), Turno(22 * 60, 6 * 60)
                ),
                context = context,
                onMudar = onTurnoB
            )
            TextButton(
                onClick = onRemoverTurnoB,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Remover segundo horário") }
        } else {
            OutlinedButton(
                onClick = onAdicionarTurnoB,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Espaco.alvoToque)
            ) {
                Icon(Icones.Adicionar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Espaco.sm))
                Text("Outro horário")
            }
        }
    }
    val turnoNoturno = listOfNotNull(turnoA, turnoB.takeIf { usaDoisHorarios }).firstOrNull { it.noturno }
    if (turnoNoturno != null) {
        SecaoDescanso(turnoNoturno, descanso, onDescanso)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecaoDescanso(turnoNoturno: Turno, descanso: Int, onDescanso: (Int) -> Unit) {
    Secao("Descanso") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
            verticalArrangement = Arrangement.spacedBy(Espaco.xs)
        ) {
            listOf(0 to "Não considerar", 240 to "4 horas", 360 to "6 horas", 480 to "8 horas")
                .forEach { (minutos, rotulo) ->
                    FilterChip(
                        selected = descanso == minutos,
                        onClick = { onDescanso(minutos) },
                        label = { Text(rotulo) }
                    )
                }
        }
        Text(
            text = if (descanso == 0) {
                "Livre logo ao sair, às ${horaCurta(turnoNoturno.fim)}."
            } else {
                "Sai às ${horaCurta(turnoNoturno.fim)} e dorme até as ${horaCurta(turnoNoturno.fim + descanso)}."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Etapa 4 da escala semanal: tocar nos dias de trabalho. */
@Composable
fun EtapaDiasSemana(
    dias: String,
    diasMarcados: Int,
    usaDoisHorarios: Boolean,
    pincel: Char,
    turnoA: Turno,
    turnoB: Turno,
    onPincel: (Char) -> Unit,
    onTocar: (Int) -> Unit
) {
    TituloEtapa(
        "Dias de trabalho",
        if (usaDoisHorarios) "Toque nos dias" else "Toque para trocar"
    )
    if (usaDoisHorarios) {
        SeletorPincel(pincel, turnoA, turnoB, onPincel)
    }
    SeletorDiasSemana(
        dias = dias,
        turnoA = turnoA,
        turnoB = turnoB.takeIf { usaDoisHorarios },
        onTocar = onTocar
    )
    val folgas = diasMarcados.inv() and 0b1111111
    CaixaInformacao(
        if (diasMarcados == 0) {
            "Nenhum dia marcado"
        } else {
            "Trabalha ${resumoDiasSemana(diasMarcados)} · " +
                if (folgas == 0) "sem folga fixa" else "folga ${resumoDiasSemana(folgas)}"
        }
    )
}

/** Etapa 4 do revezamento: descobrir o início do ciclo por perguntas ou no calendário. */
@Composable
fun EtapaDiasRevezamento(
    escala: TipoEscala,
    sujeito: String,
    noturno: Boolean,
    previa: Pessoa,
    inicio: Int,
    respostas: String,
    cicloDefinido: Boolean,
    marcarNoCalendario: Boolean,
    onMudarInicio: (Int) -> Unit,
    onUsarPerguntas: () -> Unit,
    onUsarCalendario: () -> Unit,
    onResponder: (Boolean) -> Unit,
    onRecomecar: () -> Unit
) {
    TituloEtapa(
        "Encaixe a ${escala.codigo}",
        when {
            !marcarNoCalendario -> "Sim ou não"
            escala.diasTrabalho == 1 -> "Toque em um dia de plantão."
            else -> "Toque no 1º dia de trabalho"
        }
    )
    if (marcarNoCalendario) {
        GradeRevezamento(previa = previa, inicio = inicio, onMudarInicio = onMudarInicio)
        TextButton(onClick = onUsarPerguntas) { Text("Prefiro responder perguntas") }
    } else {
        PerguntasEscala(
            escala = escala,
            sujeito = sujeito,
            noturno = noturno,
            respostas = respostas,
            previa = previa.takeIf { cicloDefinido },
            onResponder = onResponder,
            onRecomecar = onRecomecar
        )
        TextButton(onClick = onUsarCalendario) { Text("Prefiro marcar no calendário") }
    }
}

/** Etapa 5: mostra como ficou a escala nas próximas semanas. */
@Composable
fun CartaoResumoEscala(previa: Pessoa, idDaCor: Int, onCorrigirDias: () -> Unit) {
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Espaco.lg), verticalArrangement = Arrangement.spacedBy(Espaco.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(previa.nome.ifEmpty { "?" }, tamanho = 44.dp, cores = coresPessoa(previa.copy(id = idDaCor)))
                Spacer(Modifier.width(Espaco.md))
                Column(Modifier.weight(1f)) {
                    Text(previa.nome, style = MaterialTheme.typography.titleMedium)
                    Text(
                        previa.resumoEscala,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FaixaDias(
                inicio = hoje() - diaDaSemana(hoje()),
                quantidade = 35,
                status = previa::status,
                turnoDoDia = previa::turnoNoDia
            )
            Legenda(comFerias = previa.temFerias)
            TextButton(onClick = onCorrigirDias) {
                Icon(Icones.Editar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Espaco.sm))
                Text("Corrigir dias")
            }
        }
    }
}

/** Segundo emprego ou faculdade da pessoa. */
@Composable
fun SecaoAtividades(
    atividades: List<Atividade>,
    onAbrir: (Int) -> Unit,
    onNova: (TipoAtividade) -> Unit
) {
    Secao("Outras atividades") {
        atividades.forEachIndexed { i, a ->
            LinhaAtividade(a) { onAbrir(i) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
            TipoAtividade.entries.forEach { t ->
                OutlinedButton(
                    onClick = { onNova(t) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Espaco.alvoToque)
                ) {
                    Icon(Icones.Adicionar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Espaco.xs))
                    Text(t.botao, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SecaoSono(
    temSono: Boolean,
    dormir: Int,
    acordar: Int,
    turnos: List<Turno>,
    onTemSono: (Boolean) -> Unit,
    onDormir: (Int) -> Unit,
    onAcordar: (Int) -> Unit
) {
    Secao("Sono") {
        if (temSono) {
            CamposSono(dormir, acordar, turnos, onDormir = onDormir, onAcordar = onAcordar)
            TextButton(onClick = { onTemSono(false) }) { Text("Remover") }
        } else {
            OutlinedButton(
                onClick = { onTemSono(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Espaco.alvoToque)
            ) {
                Icon(Icones.Lua, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Espaco.sm))
                Text("Definir sono")
            }
        }
    }
}

/** Quem responde "não" nunca recebe a pergunta de cada feriado. */
@Composable
fun SecaoFeriados(folga: Boolean, onMudar: (Boolean) -> Unit) {
    Secao("Sua empresa costuma folgar em feriados?") {
        Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
            FilterChip(selected = folga, onClick = { onMudar(true) }, label = { Text("Sim") })
            FilterChip(selected = !folga, onClick = { onMudar(false) }, label = { Text("Não") })
        }
        Text(
            if (folga) "Perto de cada feriado, perguntamos se você vai folgar." else "Os feriados seguem a sua escala normal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BotaoRemover(nome: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Espaco.alvoToque)
    ) {
        Icon(Icones.Excluir, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Espaco.sm))
        Text("Remover $nome")
    }
}

@Composable
fun DialogoRemover(nome: String, modo: ModoEscala, onConfirmar: () -> Unit, onFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Remover $nome?") },
        text = { Text(if (modo == ModoEscala.ATIVIDADE) "Os horários dela saem da agenda." else "Dá para desfazer logo depois.") },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Remover") }
        },
        dismissButton = {
            TextButton(onClick = onFechar) { Text("Cancelar") }
        }
    )
}

/** Toque com o pincel: pintar de novo com a mesma cor volta para folga. */
fun pintar(dias: String, indice: Int, pincel: Char): String {
    val atual = dias[indice]
    val novo = if (atual == pincel) FOLGA else pincel
    return dias.substring(0, indice) + novo + dias.substring(indice + 1)
}

// ============================================================================
// Peças do cadastro
// ============================================================================

/** Atividade já cadastrada: tocar abre para editar. */
@Composable
fun LinhaAtividade(atividade: Atividade, onClick: () -> Unit) {
    Cartao(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(Espaco.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (atividade.tipo == TipoAtividade.FACULDADE) Icones.Escola else Icones.Trabalho,
                contentDescription = atividade.tipo.rotulo,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(Espaco.md))
            Column(Modifier.weight(1f)) {
                Text(atividade.nome, style = MaterialTheme.typography.titleSmall)
                Text(
                    atividade.resumo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icones.Editar, contentDescription = "Editar", modifier = Modifier.size(18.dp))
        }
    }
}

/** Etapas no topo: ícone e número, tocáveis para voltar ou, na edição, pular. O nome é só para o leitor de tela. */
@Composable
fun IndicadorEtapas(atual: Int, alcancada: Int, onIr: (Int) -> Unit) {
    val cores = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .limitarLargura()
                .fillMaxWidth()
                .padding(horizontal = Espaco.lg)
        ) {
            ETAPAS.forEachIndexed { i, etapa ->
                val disponivel = i <= alcancada
                val (fundo, tinta) = when {
                    i == atual -> cores.primary to cores.onPrimary
                    disponivel -> cores.primaryContainer to cores.onPrimaryContainer
                    else -> cores.surfaceVariant to cores.onSurfaceVariant
                }
                if (i > 0) {
                    // Linha na altura do centro dos círculos
                    Box(
                        Modifier
                            .padding(top = 19.dp)
                            .weight(1f)
                            .height(2.dp)
                            .background(if (disponivel) cores.primary else cores.outlineVariant)
                    )
                }
                Column(
                    Modifier
                        .widthIn(min = 48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = disponivel && i != atual, role = Role.Tab) { onIr(i) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(fundo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(etapa.icone, contentDescription = etapa.nome, tint = tinta, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "${i + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (i == atual) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == atual) cores.primary else cores.onSurfaceVariant,
                        modifier = Modifier.padding(top = Espaco.xs)
                    )
                }
            }
        }
    }
}

@Composable
fun TituloEtapa(titulo: String, texto: String? = null, imagem: Int? = null) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Espaco.xs),
        horizontalAlignment = if (imagem != null) Alignment.CenterHorizontally else Alignment.Start
    ) {
        if (imagem != null) {
            Image(
                painter = painterResource(imagem),
                contentDescription = null,
                modifier = Modifier
                    .height(140.dp)
                    .aspectRatio(1.5f)
            )
            Spacer(Modifier.height(Espaco.sm))
        }
        Text(
            titulo,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = if (imagem != null) TextAlign.Center else TextAlign.Start
        )
        if (texto != null) {
            Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CaixaInformacao(texto: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
            .padding(Espaco.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icones.Confirmar, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(Espaco.md))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Opção grande da etapa "Rotina". */
@Composable
fun CartaoCategoria(
    icone: ImageVector,
    titulo: String,
    texto: String,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    val forma = MaterialTheme.shapes.large
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (selecionado) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                if (selecionado) 2.dp else 1.dp,
                if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                forma
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(Espaco.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(
                    if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icone,
                contentDescription = null,
                tint = if (selecionado) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Espaco.lg))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selecionado) {
            Spacer(Modifier.width(Espaco.sm))
            Icon(Icones.Confirmar, contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Cartão de uma escala de revezamento com uma semana de exemplo desenhada. */
@Composable
fun CartaoTipoEscala(
    tipo: TipoEscala,
    selecionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forma = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(forma)
            .background(
                if (selecionado) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = if (selecionado) 2.dp else 1.dp,
                color = if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = forma
            )
            .clickable(role = Role.RadioButton, onClickLabel = "Escolher escala ${tipo.nome}", onClick = onClick)
            .padding(Espaco.md),
        verticalArrangement = Arrangement.spacedBy(Espaco.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tipo.nome, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (selecionado) {
                Icon(
                    Icones.Confirmar,
                    contentDescription = "Selecionada",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            tipo.titulo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(Espaco.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            tipo.exemplo.forEach { dia ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(
                            if (dia != 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
            }
        }
    }
}

/** Horário com atalhos, entrada/saída no relógio e prévia em barra de 24h. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorTurno(
    titulo: String,
    turno: Turno,
    sugestoes: List<Turno>,
    context: Context,
    onMudar: (Turno) -> Unit
) {
    Secao(titulo) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
            verticalArrangement = Arrangement.spacedBy(Espaco.xs)
        ) {
            sugestoes.forEach { opcao ->
                FilterChip(
                    selected = opcao == turno,
                    onClick = { onMudar(opcao) },
                    label = { Text(opcao.resumo) },
                    leadingIcon = {
                        Icon(
                            if (opcao.noturno) Icones.Lua else Icones.Sol,
                            contentDescription = if (opcao.noturno) "Noturno" else "Diurno",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Espaco.md)) {
            CampoSelecao(
                valor = horarioDeMinutos(turno.inicio),
                rotulo = "Entrada",
                icone = Icones.Relogio,
                onClick = {
                    abrirSeletorHorario(context, horarioDeMinutos(turno.inicio)) {
                        onMudar(turno.copy(inicio = minutosDoHorario(it)))
                    }
                },
                modifier = Modifier.weight(1f)
            )
            CampoSelecao(
                valor = horarioDeMinutos(turno.fim),
                rotulo = "Saída",
                icone = Icones.Relogio,
                onClick = {
                    abrirSeletorHorario(context, horarioDeMinutos(turno.fim)) {
                        onMudar(turno.copy(fim = minutosDoHorario(it)))
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        PreviaTurno(turno)
    }
}

/** Mostra o turno numa barra de 24h; turnos noturnos aparecem divididos entre os dois dias. */
@Composable
fun PreviaTurno(turno: Turno) {
    val noturno = turno.noturno
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Espaco.lg), verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (noturno) Icones.Lua else Icones.Sol,
                    contentDescription = null,
                    tint = if (noturno) MaterialTheme.colorScheme.tertiary else corAmbar(),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(Espaco.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            turno.duracao == MINUTOS_DIA -> "Plantão de 24 horas"
                            noturno -> "Turno noturno · ${turno.duracaoTexto}"
                            else -> "Turno diurno · ${turno.duracaoTexto}"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (noturno) {
                        Text(
                            "Sai às ${horaCurta(turno.fim)} do dia seguinte",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            LinhaTurno("Dia do turno", listOf(turno.inicio to minOf(turno.inicio + turno.duracao, MINUTOS_DIA)))
            if (noturno) LinhaTurno("Dia seguinte", listOf(0 to turno.inicio + turno.duracao - MINUTOS_DIA))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 96.dp)
            ) {
                listOf("0h", "6h", "12h", "18h", "24h").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LinhaTurno(rotulo: String, ocupados: List<Pair<Int, Int>>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            rotulo,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        BarraHorarios(
            ocupados = ocupados,
            corLivre = MaterialTheme.colorScheme.primaryContainer,
            corOcupado = MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Escolha do "pincel" para pintar os dias: horário principal, segundo horário ou folga. */
@Composable
fun SeletorPincel(pincel: Char, turnoA: Turno, turnoB: Turno?, onEscolher: (Char) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Espaco.sm)
    ) {
        listOfNotNull(TURNO_A to turnoA, turnoB?.let { TURNO_B to it }, FOLGA to null).forEach { (letra, turno) ->
            FilterChip(
                selected = pincel == letra,
                onClick = { onEscolher(letra) },
                label = { Text(turno?.resumo ?: "Folga") },
                leadingIcon = {
                    Icon(
                        when {
                            turno == null -> Icones.DiaLivre
                            turno.noturno -> Icones.Lua
                            else -> Icones.Sol
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

/** Ícone e rótulo curto de um dia do ciclo ("7h", "Folga"). */
@Composable
fun ConteudoDiaCiclo(letra: Char, turnoA: Turno, turnoB: Turno?) {
    val turno = when (letra) {
        TURNO_A -> turnoA
        TURNO_B -> turnoB ?: turnoA
        else -> null
    }
    val status = if (turno == null) StatusDia.FOLGA else StatusDia.TRABALHO
    Icon(
        when {
            turno == null -> Icones.DiaLivre
            turno.noturno -> Icones.Lua
            else -> Icones.Trabalho
        },
        contentDescription = null,
        tint = corConteudo(status),
        modifier = Modifier.size(16.dp)
    )
    Text(
        text = turno?.let { horaCurta(it.inicio) } ?: "Folga",
        style = MaterialTheme.typography.labelSmall,
        color = corConteudo(status),
        maxLines = 1
    )
}

/** Sete botões da semana: tocar alterna trabalho e folga (ou pinta com o pincel escolhido). */
@Composable
fun SeletorDiasSemana(dias: String, turnoA: Turno, turnoB: Turno?, onTocar: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { d ->
            val letra = dias.getOrElse(d) { FOLGA }
            val status = if (letra == FOLGA) StatusDia.FOLGA else StatusDia.TRABALHO
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    nomeCurtoDiaSemana(d).capitalizar(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Espaco.xs))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(corFundo(status))
                        .border(
                            1.dp,
                            corIndicador(status).copy(alpha = 0.4f),
                            MaterialTheme.shapes.medium
                        )
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Alterar ${nomeCurtoDiaSemana(d)}"
                        ) { onTocar(d) }
                        .padding(vertical = Espaco.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                ) {
                    ConteudoDiaCiclo(letra, turnoA, turnoB)
                }
            }
        }
    }
}

/** Perguntas "trabalha hoje? e amanhã?" que descobrem o início do ciclo. */
@Composable
fun PerguntasEscala(
    escala: TipoEscala,
    sujeito: String,
    noturno: Boolean,
    respostas: String,
    previa: Pessoa?,
    onResponder: (Boolean) -> Unit,
    onRecomecar: () -> Unit
) {
    val restantes = candidatos(escala, respostas)
    val diaPergunta = hoje() + respostas.length
    val quando = when (respostas.length) {
        0 -> "hoje"
        1 -> "amanhã"
        2 -> "depois de amanhã"
        else -> "em ${diaResumido(diaPergunta)}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(Espaco.md)) {
        // Respostas já dadas
        respostas.forEachIndexed { i, r ->
            val dia = hoje() + i
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeloStatus(if (r == 'S') StatusDia.TRABALHO else StatusDia.FOLGA)
                Spacer(Modifier.width(Espaco.md))
                Text(
                    "${rotuloRelativo(dia)} · ${diaResumido(dia)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            restantes.isEmpty() -> {
                Cartao(Modifier.fillMaxWidth(), cor = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(Espaco.lg), verticalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                        Text(
                            "Não combina com ${escala.codigo}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Confira as respostas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        FilledTonalButton(onClick = onRecomecar) { Text("Responder de novo") }
                    }
                }
            }

            restantes.size == 1 && previa != null -> {
                Cartao(Modifier.fillMaxWidth(), cor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(Espaco.lg), verticalArrangement = Arrangement.spacedBy(Espaco.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icones.DiaLivre, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Espaco.md))
                            Text(
                                "Pronto! Encontramos a escala.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
                                .padding(Espaco.md)
                        ) {
                            FaixaDias(
                                inicio = hoje() - diaDaSemana(hoje()),
                                quantidade = 14,
                                status = previa::status,
                                turnoDoDia = previa::turnoNoDia
                            )
                        }
                        TextButton(onClick = onRecomecar) { Text("Responder de novo") }
                    }
                }
            }

            else -> {
                Cartao(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(Espaco.lg),
                        verticalArrangement = Arrangement.spacedBy(Espaco.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SeloData(diaPergunta, destaque = true)
                        Text(
                            "$sujeito ${if (escala.diasTrabalho == 1) "tem plantão" else "trabalha"} $quando?",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        if (noturno) {
                            Text(
                                "Noite conta no dia em que começa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Espaco.md)) {
                            Button(
                                onClick = { onResponder(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                            ) {
                                Icon(Icones.Trabalho, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Espaco.sm))
                                Text("Sim, trabalha")
                            }
                            OutlinedButton(
                                onClick = { onResponder(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                            ) {
                                Icon(Icones.DiaLivre, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Espaco.sm))
                                Text("Não, folga")
                            }
                        }
                        if (respostas.isNotEmpty()) {
                            TextButton(onClick = onRecomecar) { Text("Começar de novo") }
                        }
                    }
                }
            }
        }
    }
}

/** Revezamento no calendário: toque no dia de trabalho e ajuste fino de um dia. */
@Composable
fun GradeRevezamento(previa: Pessoa, inicio: Int, onMudarInicio: (Int) -> Unit) {
    val inicioGrade = inicioDaGrade()
    val ciclo = previa.padrao.size
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Espaco.lg), verticalArrangement = Arrangement.spacedBy(Espaco.md)) {
            FaixaDias(
                inicio = inicioGrade,
                quantidade = 21,
                status = previa::status,
                marcado = inicio,
                turnoDoDia = previa::turnoNoDia,
                onToque = onMudarInicio
            )
            Text(
                "Dia marcado: ${diaResumido(inicio)}. Não bateu? Toque em outro dia ou ajuste.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                OutlinedButton(
                    onClick = {
                        onMudarInicio((inicio - 1).let { if (it < inicioGrade) aproximarInicio(it, ciclo) else it })
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icones.Anterior, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Espaco.xs))
                    Text("1 dia antes")
                }
                OutlinedButton(
                    onClick = {
                        onMudarInicio((inicio + 1).let { if (it >= inicioGrade + 21) aproximarInicio(it, ciclo) else it })
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("1 dia depois")
                    Spacer(Modifier.width(Espaco.xs))
                    Icon(Icones.Proximo, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Legenda(comFerias = false)
        }
    }
}

/** Escala personalizada: modelos, tamanho do ciclo, pincel e o ciclo pintado dia a dia. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorCiclo(
    dias: String,
    inicio: Int,
    turnoA: Turno,
    turnoB: Turno?,
    pincel: Char,
    context: Context,
    onPincel: (Char) -> Unit,
    onDias: (String) -> Unit,
    onInicio: (Int) -> Unit,
    onModelo: (ModeloPersonalizado) -> Unit
) {
    Secao("Comece por um modelo") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
            verticalArrangement = Arrangement.spacedBy(Espaco.xs)
        ) {
            MODELOS_PERSONALIZADOS.forEach { m ->
                FilterChip(
                    selected = dias == m.dias && turnoA == m.turnoA && turnoB == m.turnoB,
                    onClick = { onModelo(m) },
                    label = { Text(m.nome) }
                )
            }
        }
    }

    Secao("Tamanho do ciclo") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { if (dias.length > 2) onDias(dias.dropLast(1)) },
                enabled = dias.length > 2
            ) { Icon(Icones.Anterior, contentDescription = "Diminuir ciclo") }
            Text(
                "${dias.length} dias",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = { if (dias.length < MAX_DIAS_CICLO) onDias(dias + FOLGA) },
                enabled = dias.length < MAX_DIAS_CICLO
            ) { Icon(Icones.Proximo, contentDescription = "Aumentar ciclo") }
        }
    }

    Secao("Pinte os dias") {
        if (turnoB != null) {
            SeletorPincel(pincel, turnoA, turnoB, onPincel)
        } else {
            Text(
                "Toque para trocar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            dias.indices.chunked(7).forEach { semana ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    semana.forEach { i ->
                        val letra = dias[i]
                        val status = if (letra == FOLGA) StatusDia.FOLGA else StatusDia.TRABALHO
                        val data = inicio + i
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(corFundo(status))
                                .border(1.dp, corIndicador(status).copy(alpha = 0.4f), MaterialTheme.shapes.small)
                                .clickable(role = Role.Button, onClickLabel = "Alterar dia ${i + 1}") {
                                    onDias(pintar(dias, i, if (turnoB != null) pincel else TURNO_A))
                                }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.labelLarge.numerico,
                                color = corConteudo(status)
                            )
                            ConteudoDiaCiclo(letra, turnoA, turnoB)
                            Text(
                                "${diaSemanaCurto(data)} ${partes(data).dia}",
                                style = MaterialTheme.typography.labelSmall,
                                color = corConteudo(status).copy(alpha = 0.75f),
                                maxLines = 1
                            )
                        }
                    }
                    repeat(7 - semana.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    Secao("Quando começa o dia 1?") {
        CampoSelecao(
            valor = diaPorExtenso(inicio),
            rotulo = "Dia 1 do ciclo",
            icone = Icones.Calendario,
            onClick = { abrirSeletorData(context, inicio, onInicio) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Férias: botão para adicionar ou as duas datas. */
@Composable
fun CampoFerias(
    temFerias: Boolean,
    inicio: Int,
    fim: Int,
    erroInicio: String?,
    erroFim: String?,
    context: Context,
    onAtivar: (Boolean) -> Unit,
    onInicio: (Int) -> Unit,
    onFim: (Int) -> Unit
) {
    if (!temFerias) {
        OutlinedButton(
            onClick = { onAtivar(true) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Espaco.alvoToque)
        ) {
            Icon(Icones.Adicionar, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Espaco.sm))
            Text("Adicionar férias")
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.md)) {
        CampoSelecao(
            valor = if (inicio == SEM_DATA) "" else diaParaTexto(inicio),
            rotulo = "Início",
            placeholder = "Escolher",
            icone = Icones.Calendario,
            erro = erroInicio,
            onClick = { abrirSeletorData(context, if (inicio == SEM_DATA) hoje() else inicio, onInicio) },
            modifier = Modifier.weight(1f)
        )
        CampoSelecao(
            valor = if (fim == SEM_DATA) "" else diaParaTexto(fim),
            rotulo = "Fim",
            placeholder = "Escolher",
            icone = Icones.Calendario,
            erro = erroFim,
            onClick = {
                val base = when {
                    fim != SEM_DATA -> fim
                    inicio != SEM_DATA -> inicio + 29
                    else -> hoje()
                }
                abrirSeletorData(context, base, onFim)
            },
            modifier = Modifier.weight(1f)
        )
    }
    if (inicio != SEM_DATA && fim != SEM_DATA && fim >= inicio) {
        Text(
            text = "${fim - inicio + 1} dias de férias",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
    TextButton(
        onClick = { onAtivar(false) },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Text("Remover férias")
    }
}
