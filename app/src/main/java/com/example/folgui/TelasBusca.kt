package com.example.folgui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ============================================================================
// QUANDO DÁ? Busca os próximos horários em que as pessoas escolhidas estão livres
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaQuandoDa(
    todas: List<Pessoa>,
    feriados: Map<Int, String>,
    onVoltar: () -> Unit,
    onAbrirDia: (Int) -> Unit,
    onPlanejar: (Encontro, Set<Int>?) -> Unit
) {
    val outras = todas.filter { !it.ehVoce }
    // "*" = todas as pessoas; senão, os ids de quem entra na busca
    var quem by rememberSaveable { mutableStateOf("*") }
    var duracao by rememberSaveable { mutableIntStateOf(120) }
    var periodo by rememberSaveable { mutableStateOf(PeriodoDia.QUALQUER.name) }
    var soFimDeSemana by rememberSaveable { mutableStateOf(false) }

    val participantes = textoParaParticipantes(quem)
    val grupo = todas.filter { it.ehVoce || participantes == null || it.id in participantes }
    val periodoDia = PeriodoDia.valueOf(periodo)
    val encontros = remember(grupo, duracao, periodoDia, soFimDeSemana, feriados.size) {
        // Folga de todos primeiro; depois, os dias em que só sobra parte do tempo
        proximosEncontros(grupo, duracao, periodoDia, soFimDeSemana, feriados.keys, quantidade = 15)
            .sortedBy { !todosLivres(grupo, it.dia) }
    }

    fun alternar(id: Int) {
        val atuais = participantes ?: outras.map { it.id }.toSet()
        val novos = if (id in atuais) atuais - id else atuais + id
        quem = participantesParaTexto(if (novos == outras.map { it.id }.toSet()) null else novos)
    }

    Scaffold(topBar = { BarraSuperior("Quando dá?", onVoltar = onVoltar) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = Espaco.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "filtros") {
                Column(
                    Modifier
                        .limitarLargura()
                        .padding(horizontal = Espaco.lg),
                    verticalArrangement = Arrangement.spacedBy(Espaco.lg)
                ) {
                    Secao("Quem") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
                            verticalArrangement = Arrangement.spacedBy(Espaco.xs)
                        ) {
                            FilterChip(selected = true, onClick = {}, enabled = false, label = { Text("Você") })
                            outras.forEach { p ->
                                FilterChip(
                                    selected = grupo.any { it.id == p.id },
                                    onClick = { alternar(p.id) },
                                    label = { Text(p.nome) },
                                    leadingIcon = { Avatar(p.nome, tamanho = 20.dp, cores = coresPessoa(p)) }
                                )
                            }
                        }
                    }
                    Secao("Duração") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
                            verticalArrangement = Arrangement.spacedBy(Espaco.xs)
                        ) {
                            OPCOES_DURACAO.forEach { minutos ->
                                FilterChip(
                                    selected = duracao == minutos,
                                    onClick = { duracao = minutos },
                                    label = { Text("${minutos / 60}h") }
                                )
                            }
                        }
                    }
                    Secao("Período") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Espaco.sm),
                            verticalArrangement = Arrangement.spacedBy(Espaco.xs)
                        ) {
                            PeriodoDia.entries.forEach { p ->
                                FilterChip(
                                    selected = periodoDia == p,
                                    onClick = { periodo = p.name },
                                    label = { Text(p.rotulo) },
                                    leadingIcon = when (p) {
                                        PeriodoDia.NOITE -> { { Icon(Icones.Lua, null, Modifier.size(18.dp)) } }
                                        PeriodoDia.MANHA, PeriodoDia.TARDE -> { { Icon(Icones.Sol, null, Modifier.size(18.dp)) } }
                                        else -> null
                                    }
                                )
                            }
                            FilterChip(
                                selected = soFimDeSemana,
                                onClick = { soFimDeSemana = !soFimDeSemana },
                                label = { Text("Fim de semana") },
                                leadingIcon = if (soFimDeSemana) {
                                    { Icon(Icones.Confirmar, null, Modifier.size(18.dp)) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }

            item(key = "titulo") {
                TituloSecao(
                    when {
                        encontros.isEmpty() -> "Sem horários"
                        grupo.size > 1 -> "Melhores dias para vocês se encontrarem"
                        else -> "Melhores dias"
                    },
                    modifier = Modifier
                        .limitarLargura()
                        .padding(top = Espaco.md)
                )
            }

            if (encontros.isEmpty()) {
                item(key = "vazio") {
                    EstadoVazio(
                        icone = Icones.Relogio,
                        titulo = "Nada encontrado",
                        imagem = R.drawable.ilustracao_turnos,
                        texto = "Tente menos tempo ou menos pessoas.",
                        modifier = Modifier.limitarLargura()
                    )
                }
            }

            items(encontros, key = { it.dia }) { e ->
                Cartao(
                    modifier = Modifier
                        .limitarLargura()
                        .padding(horizontal = Espaco.lg, vertical = Espaco.xs),
                    onClick = { onAbrirDia(e.dia) }
                ) {
                    Row(
                        Modifier.padding(Espaco.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SeloData(e.dia, destaque = true)
                        Spacer(Modifier.width(Espaco.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${nomeDiaSemana(e.dia).capitalizar()} · ${rotuloRelativo(e.dia).lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                faixaHoras(e.janela.inicio, e.janela.fim).capitalizar(),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = listOfNotNull(
                                    if (situacaoGrupo(grupo, e.dia) == SituacaoGrupo.TODOS_LIVRES) {
                                        motivoLivres(grupo, e.dia)
                                    } else {
                                        "Fora do horário de trabalho"
                                    },
                                    feriados[e.dia]?.let { "Feriado: $it" }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(Espaco.sm))
                        FilledTonalButton(onClick = { onPlanejar(e, participantes) }) { Text("Marcar") }
                    }
                }
            }
        }
    }
}
