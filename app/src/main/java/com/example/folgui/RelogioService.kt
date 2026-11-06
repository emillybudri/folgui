package com.example.folgui

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Recebe o que a pessoa falou no relógio, mesmo com o app fechado.
 * Tudo acontece aqui no celular: o relógio só escuta, mostra e confirma.
 */
class RelogioService : WearableListenerService() {

    private companion object {
        // Fica fora do serviço: o Android pode recriá-lo entre o pedido e o "sim"
        val conversa = ConversaRelogio()
    }

    // Esta função já roda fora da thread principal, então esperar a IA aqui não trava o app.
    // O runBlocking mantém o serviço vivo até a resposta sair
    override fun onMessageReceived(mensagem: MessageEvent) {
        val resposta = runBlocking {
            try {
                when (mensagem.path) {
                    CAMINHO_PEDIDO -> entenderPedido(String(mensagem.data))
                    CAMINHO_CONFIRMAR -> marcar()
                    CAMINHO_CANCELAR -> {
                        conversa.esquecer()
                        null
                    }
                    else -> null
                }
            } catch (e: ErroIA) {
                RespostaRelogio(TipoRespostaRelogio.ERRO, e.message ?: "Algo deu errado.")
            } catch (e: Exception) {
                RespostaRelogio(TipoRespostaRelogio.ERRO, "Sem conexão com a IA. Veja a internet do celular.")
            }
        } ?: return

        Wearable.getMessageClient(this)
            .sendMessage(mensagem.sourceNodeId, CAMINHO_RESPOSTA, respostaParaJson(resposta).toByteArray())
    }

    private suspend fun entenderPedido(pedido: String): RespostaRelogio {
        val estado = lerEstado(dataStore.data.first())
        val perfil = estado.perfil ?: return RespostaRelogio(
            TipoRespostaRelogio.ERRO,
            "Abra o Folgui no celular e monte sua escala primeiro."
        )
        val repository = FolguiRepository(this)
        val eventos = repository.listarEventos()
        val voce = perfil.comoPessoa(repository.listarExcecoes()[Pessoa.ID_VOCE].orEmpty())
        val todas = listOf(voce) + repository.listarPessoas()
        val agenda = if (estado.agendaConectada) {
            semEventosDoFolgui(AgendaRepository(this).buscar(hoje() - 1, hoje() + 400), eventos)
        } else {
            emptyMap()
        }

        val assistente = AssistenteIA(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL)
        val contexto = contextoParaIA(todas, eventos, agenda, emptyMap())
        val historico = conversa.historico(System.currentTimeMillis())
        val respostaIA = assistente.conversar(contexto, historico, todas, eventos, pedido)
        // Revisar uma lista inteira não cabe na tela do relógio
        if (respostaIA.ajuste != null) {
            return RespostaRelogio(TipoRespostaRelogio.ERRO, "Vários compromissos de uma vez, só pelo celular.")
        }
        val resposta = respostaIA.mensagem

        // Mesma conferência do assistente do celular: conflito com trabalho, sono, data que já passou
        val (validas, avisos) = verificarAcoes(resposta.acoes, todas, eventos)
        val conferida = resposta.copy(
            acoes = validas,
            avisos = resposta.avisos + avisos,
            descricoes = validas.map { descreverAcao(it, todas, eventos) }
        )
        conversa.registrar(pedido, conferida, System.currentTimeMillis())
        return respostaParaRelogio(conferida)
    }

    /** Avisa os amigos escolhidos. Se falhar, o evento continua marcado: o convite é um extra. */
    private suspend fun convidarAmigos(eventos: List<Evento>, pessoas: List<Pessoa>, meuNome: String): List<String> {
        if (BuildConfig.GOOGLE_WEB_CLIENT.isEmpty()) return emptyList()
        val amigos = AmigosRepository()
        return try {
            eventos.flatMap { evento ->
                amigosDoEvento(evento, pessoas).map { amigo ->
                    amigos.enviarConvite(amigo, evento, meuNome)
                    amigo.nome
                }
            }.distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun marcar(): RespostaRelogio {
        val acoes = conversa.retirarProposta(System.currentTimeMillis())
        if (acoes.isEmpty()) {
            return RespostaRelogio(TipoRespostaRelogio.ERRO, "Esse pedido expirou. Fale de novo, por favor.")
        }

        val repository = FolguiRepository(this)
        val agenda = AgendaRepository(this)
        val estado = lerEstado(dataStore.data.first())
        val eventos = repository.listarEventos()
        val marcados = acoes.mapNotNull { acao ->
            val novo = eventoResultante(acao, eventos) ?: return@mapNotNull null
            val id = repository.inserirEvento(novo)
            if (id <= 0) return@mapNotNull null
            val criado = novo.copy(id = id)
            AlarmScheduler.schedule(this, criado)
            // Aqui não dá para pedir permissão: sem ela, o evento fica só no Folgui
            if (!estado.agendaDesligada && agenda.temEscrita()) {
                val gravado = agenda.salvar(criado, 0)
                gravado.id?.let { repository.salvarAgendaId(id, it) }
                if (gravado.avisosGravados) {
                    repository.marcarAvisoNaAgenda(id, true)
                    AlarmScheduler.cancel(this, id)
                }
            }
            criado
        }
        if (marcados.isEmpty()) {
            return RespostaRelogio(TipoRespostaRelogio.ERRO, "Não consegui salvar. Tente pelo celular.")
        }
        mudancasDoRelogio.value++
        val convidados = convidarAmigos(marcados, repository.listarPessoas(), estado.perfil?.nome.orEmpty())
        return RespostaRelogio(
            TipoRespostaRelogio.MARCADO,
            marcados.joinToString("\n") { "${it.titulo}, ${diaResumido(it.dia)}, ${it.faixaHorario}" } +
                if (convidados.isEmpty()) "" else "\nConvite para ${convidados.joinToString(", ")}"
        )
    }
}
