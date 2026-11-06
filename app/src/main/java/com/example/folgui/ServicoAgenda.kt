package com.example.folgui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mantém o Folgui e a Google Agenda iguais nas operações que mexem nos dois: criar em lote,
 * pintar eventos e conciliar o que mudou direto na agenda. Tudo roda fora da thread principal.
 */
class ServicoAgenda(
    context: Context,
    private val repository: FolguiRepository,
    private val agenda: AgendaRepository
) {
    private val appContext = context.applicationContext

    /**
     * Cria um evento do lote. A agenda vem primeiro: se ela falhar, nada fica pela metade e o
     * item segue pendente. Com [soFolgui], a pessoa escolheu não usar a agenda.
     */
    suspend fun criar(evento: Evento, cor: CorGrupo?, soFolgui: Boolean = false): ResultadoCriacao =
        withContext(Dispatchers.IO) {
            if (soFolgui) {
                val id = repository.inserirEvento(evento)
                if (id <= 0) return@withContext ResultadoCriacao("Não consegui salvar no Folgui")
                AlarmScheduler.schedule(appContext, evento.copy(id = id))
                return@withContext ResultadoCriacao()
            }

            val gravado = agenda.salvar(evento, 0, cor)
            val agendaId = gravado.id
                ?: return@withContext ResultadoCriacao(gravado.falha?.mensagem ?: FalhaAgenda.ERRO.mensagem)
            val id = repository.inserirEvento(evento)
            if (id <= 0) {
                agenda.excluir(agendaId)
                return@withContext ResultadoCriacao("Não consegui salvar no Folgui")
            }
            repository.salvarAgendaId(id, agendaId)
            repository.marcarAvisoNaAgenda(id, gravado.avisosGravados)
            // Sem lembrete na agenda, quem avisa é o alarme do Folgui
            if (!gravado.avisosGravados) AlarmScheduler.schedule(appContext, evento.copy(id = id))
            ResultadoCriacao()
        }

    /** Pinta os eventos na agenda (null = cor padrão). Retorna os que não deu para pintar. */
    suspend fun pintar(eventos: List<Evento>, cor: CorGrupo?): List<Evento> = withContext(Dispatchers.IO) {
        eventos.filter { it.agendaId > 0 && !it.foraDaAgenda }.filterNot { agenda.aplicarCor(it.agendaId, cor) }
    }

    /** Move para o grupo (0 = sem grupo) e acerta a cor. Retorna os que não deu para pintar. */
    suspend fun mover(eventos: List<Evento>, grupo: Grupo?): List<Evento> {
        withContext(Dispatchers.IO) { repository.moverParaGrupo(eventos.map { it.id }, grupo?.id ?: 0) }
        return pintar(eventos, grupo?.cor)
    }

    /** Traz para o Folgui um evento criado direto na agenda, sem mudar nada nele lá. */
    suspend fun importar(compromisso: CompromissoAgenda, grupo: Grupo): Boolean = withContext(Dispatchers.IO) {
        val lido = agenda.lerEventos(listOf(compromisso.eventoId))?.get(compromisso.eventoId) ?: return@withContext false
        val evento = Evento(
            titulo = compromisso.titulo,
            dia = diaDoMomento(compromisso.inicio),
            horario = horarioDoMomento(compromisso.inicio),
            local = compromisso.local,
            antecedenciaMin = -1,
            duracaoMin = ((compromisso.fim - compromisso.inicio) / 60_000L).toInt().coerceIn(1, MINUTOS_DIA),
            participantes = emptySet(),
            grupoId = grupo.id
        ).comAvisos(lido.avisos)
        val id = repository.inserirEvento(evento)
        if (id <= 0) return@withContext false
        repository.salvarAgendaId(id, compromisso.eventoId)
        repository.marcarAvisoNaAgenda(id, true)
        agenda.aplicarCor(compromisso.eventoId, grupo.cor)
        true
    }

    /** Devolve à agenda um evento que foi apagado lá. */
    suspend fun restaurar(evento: Evento, cor: CorGrupo?): Boolean = withContext(Dispatchers.IO) {
        val gravado = agenda.salvar(evento.copy(agendaId = 0), 0, cor)
        val id = gravado.id ?: return@withContext false
        repository.salvarAgendaId(evento.id, id)
        repository.marcarForaDaAgenda(evento.id, false)
        repository.marcarAvisoNaAgenda(evento.id, gravado.avisosGravados)
        true
    }

    /** Aplica o que mudou direto na agenda. Retorna true se algo mudou no Folgui. */
    suspend fun conciliar(): Boolean = withContext(Dispatchers.IO) {
        val eventos = repository.listarEventos()
        val ids = eventos.map { it.agendaId }.filter { it > 0 }
        val lidos = agenda.lerEventos(ids) ?: return@withContext false
        val grupos = repository.listarGrupos()
        val c = conciliarAgenda(eventos, lidos, grupos)

        c.atualizados.forEach {
            repository.atualizarEvento(it)
            AlarmScheduler.schedule(appContext, it)
        }
        c.apagados.forEach {
            repository.marcarForaDaAgenda(it.id, true)
            AlarmScheduler.cancel(appContext, it.id)
        }
        c.reapareceram.forEach { repository.marcarForaDaAgenda(it.id, false) }
        c.recolorir.forEach { e -> grupoDoEvento(e, grupos)?.let { agenda.aplicarCor(e.agendaId, it.cor) } }

        // Evento vindo de outro aparelho: a agenda já tem os lembretes, então ela é quem avisa
        eventos.filter { !it.avisoNaAgenda && it.avisos.isNotEmpty() && lidos[it.agendaId]?.avisos == it.avisos }
            .forEach {
                repository.marcarAvisoNaAgenda(it.id, true)
                AlarmScheduler.cancel(appContext, it.id)
            }
        c.atualizados.isNotEmpty() || c.apagados.isNotEmpty() || c.reapareceram.isNotEmpty()
    }
}
