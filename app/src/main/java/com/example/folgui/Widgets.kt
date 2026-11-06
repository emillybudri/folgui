package com.example.folgui

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// ============================================================================
// Widgets da tela inicial: Hoje, Semana e Compromissos.
//
// Feitos para quase não gastar memória nem bateria:
// - RemoteViews puro, sem bibliotecas, sem imagens e sem serviço rodando;
// - nenhuma chamada à internet: tudo vem do que já está no aparelho;
// - o app só roda quando algo mudou. Não há atualização periódica: um único alarme marca o
//   próximo momento em que a tela ficaria errada, e esse alarme nunca acorda o celular;
// - claro e escuro vêm dos recursos, então trocar o tema não executa nada aqui.
// ============================================================================

private const val TAG = "FolguiWidgets"

/** Alarme sem hora exata: o Android junta com outros e não pede permissão especial. */
private const val JANELA_ALARME_MS = 10 * 60_000L
private const val INTERVALO_MINIMO_MS = 60_000L

/** Depois disso o Android mataria o app por demorar no receiver. */
private const val LIMITE_LEITURA_MS = 8_000L
private const val DIAS_AGENDA = 30
private const val DIAS_SEMANA_WIDGET = 7

// Abaixo destes tamanhos, em dp, a informação extra sai para o principal continuar legível
private const val LARGURA_DUAS_PILULAS = 230
private const val ALTURA_COM_COMPROMISSO = 170
private const val ALTURA_COM_CABECALHO = 110

sealed interface DadosWidget {
    /** Sem dados para mostrar: nada pessoal aparece na tela inicial. */
    data class Aviso(val texto: String) : DadosWidget

    data class Pronto(
        val voce: Pessoa,
        val itens: List<ItemWidget>,
        val diasComEvento: Set<Int>,
        val diaHoje: Int
    ) : DadosWidget
}

data class TamanhoWidget(val largura: Int, val altura: Int)

object WidgetsFolgui {
    private const val ACAO_ATUALIZAR = "com.example.folgui.ATUALIZAR_WIDGETS"

    /** Pede para redesenhar. Quem não usa widget paga só uma consulta ao sistema. */
    fun pedirAtualizacao(context: Context) {
        context.sendBroadcast(intentAtualizar(context))
    }

    private fun intentAtualizar(context: Context) =
        Intent(context, AtualizaWidgetsReceiver::class.java).setAction(ACAO_ATUALIZAR)

    private fun alarme(context: Context) = PendingIntent.getBroadcast(
        context, 0, intentAtualizar(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun tipos() = listOf(WidgetHoje(), WidgetSemana(), WidgetCompromissos())

    private fun idsDe(context: Context, widget: WidgetFolgui): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, widget.javaClass))

    fun temAlgumWidget(context: Context): Boolean = tipos().any { idsDe(context, it).isNotEmpty() }

    suspend fun desenharTodos(context: Context) {
        val dados = ler(context)
        tipos().forEach { widget -> widget.desenhar(context, idsDe(context, widget), dados) }
        agendar(context, dados)
    }

    /** RTC, e não RTC_WAKEUP: com a tela apagada ele espera, e dispara quando o celular acordar sozinho. */
    fun agendar(context: Context, dados: DadosWidget) {
        val pronto = dados as? DadosWidget.Pronto
        if (pronto == null) {
            cancelarAlarme(context)
            return
        }
        val momento = maxOf(proximoDesenho(pronto.voce, pronto.itens), System.currentTimeMillis() + INTERVALO_MINIMO_MS)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.setWindow(AlarmManager.RTC, momento, JANELA_ALARME_MS, alarme(context))
    }

    fun cancelarAlarme(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarme(context))
    }

    suspend fun ler(context: Context): DadosWidget {
        val nuvem = NuvemRepository()
        if (nuvem.configurada && nuvem.contaAtual == null) return DadosWidget.Aviso("Entre na sua conta")

        val estado = lerEstado(context.dataStore.data.first())
        val perfil = estado.perfil ?: return DadosWidget.Aviso("Monte sua escala")

        val repository = FolguiRepository(context)
        val agora = System.currentTimeMillis()
        val diaHoje = hoje()
        // Um dia para trás alcança o evento que começou ontem e ainda não acabou
        val eventos = repository.listarEventosDesde(momentoEm(diaHoje - 1, "00:00"))
        val agenda = if (estado.agendaConectada) lerAgenda(context, eventos, diaHoje) else emptyMap()
        return DadosWidget.Pronto(
            voce = perfil.comoPessoa(repository.listarExcecoes()[Pessoa.ID_VOCE].orEmpty()),
            itens = itensDoWidget(eventos, repository.listarGrupos(), agenda, agora, diaHoje),
            diasComEvento = eventos.filterNot { it.foraDaAgenda }.map { it.dia }.toSet() + agenda.keys,
            diaHoje = diaHoje
        )
    }

    /** Sem a agenda, o widget continua com o que está no Folgui. */
    private suspend fun lerAgenda(context: Context, eventos: List<Evento>, diaHoje: Int) = try {
        semEventosDoFolgui(AgendaRepository(context).buscar(diaHoje, diaHoje + DIAS_AGENDA), eventos)
    } catch (e: Exception) {
        Log.w(TAG, "Agenda indisponível", e)
        emptyMap()
    }
}

/** Sai da thread principal e segura o app vivo só até o desenho terminar. */
private fun BroadcastReceiver.emSegundoPlano(bloco: suspend () -> Unit) {
    val pendente = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            withTimeout(LIMITE_LEITURA_MS) { bloco() }
        } catch (e: Exception) {
            // O widget fica com o último desenho, que é melhor do que derrubar o app
            Log.w(TAG, "Widgets não atualizados", e)
        } finally {
            pendente.finish()
        }
    }
}

/** Alarme, pedido do app e mudança de relógio ou fuso, que deixam a data errada. */
class AtualizaWidgetsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!WidgetsFolgui.temAlgumWidget(context)) {
            WidgetsFolgui.cancelarAlarme(context)
            return
        }
        emSegundoPlano { WidgetsFolgui.desenharTodos(context) }
    }
}

abstract class WidgetFolgui : AppWidgetProvider() {

    protected abstract fun montar(context: Context, dados: DadosWidget.Pronto, tamanho: TamanhoWidget): RemoteViews

    // updatePeriodMillis é 0: isto só roda ao adicionar o widget, ao reiniciar e ao atualizar o app
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        atualizar(context, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, opcoes: Bundle) {
        atualizar(context, intArrayOf(id))
    }

    override fun onDisabled(context: Context) {
        if (!WidgetsFolgui.temAlgumWidget(context)) WidgetsFolgui.cancelarAlarme(context)
    }

    private fun atualizar(context: Context, ids: IntArray) {
        emSegundoPlano {
            val dados = WidgetsFolgui.ler(context)
            desenhar(context, ids, dados)
            WidgetsFolgui.agendar(context, dados)
        }
    }

    fun desenhar(context: Context, ids: IntArray, dados: DadosWidget) {
        val manager = AppWidgetManager.getInstance(context)
        ids.forEach { id ->
            val views = when (dados) {
                is DadosWidget.Aviso -> viewsAviso(context, dados.texto)
                is DadosWidget.Pronto -> montar(context, dados, tamanhoDe(context, manager, id))
            }
            manager.updateAppWidget(id, views)
        }
    }

    /** No retrato o widget usa a menor largura e a maior altura que o launcher informa. */
    private fun tamanhoDe(context: Context, manager: AppWidgetManager, id: Int): TamanhoWidget {
        val opcoes = manager.getAppWidgetOptions(id)
        val retrato = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return TamanhoWidget(
            opcoes.getInt(
                if (retrato) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            ),
            opcoes.getInt(
                if (retrato) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            )
        )
    }
}

class WidgetHoje : WidgetFolgui() {
    override fun montar(context: Context, dados: DadosWidget.Pronto, tamanho: TamanhoWidget): RemoteViews {
        val voce = dados.voce
        val dia = dados.diaHoje
        val status = voce.status(dia)
        val noturno = voce.turnosNoDia(dia).firstOrNull()?.noturno == true
        val proximo = voce.proximoEstado(dia)
        // Nas férias o título já diz tudo: a pílula conta quando elas acabam
        val agora = proximo.takeIf { status == StatusDia.FERIAS } ?: voce.agoraCurto()
        val depois = proximo.takeIf { status != StatusDia.FERIAS && tamanho.largura >= LARGURA_DUAS_PILULAS }
        val item = dados.itens.firstOrNull().takeIf { tamanho.altura >= ALTURA_COM_COMPROMISSO }

        return RemoteViews(context.packageName, R.layout.widget_hoje).apply {
            setInt(android.R.id.background, "setBackgroundResource", fundoDe(status))
            setOnClickPendingIntent(android.R.id.background, abrir(context, linkDia(dia)))
            setTextViewText(R.id.widget_data, diaResumido(dia).capitalizar())
            setTextViewText(R.id.widget_status, if (status == StatusDia.TRABALHO) "Trabalho" else status.rotulo)
            setImageViewResource(R.id.widget_icone, iconeDe(status, noturno))
            setTextViewText(R.id.widget_agora, agora)
            setTextViewText(R.id.widget_depois, depois.orEmpty())
            setViewVisibility(R.id.widget_depois, if (depois == null) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.widget_compromisso, if (item == null) View.GONE else View.VISIBLE)
            if (item != null) {
                setTextViewText(R.id.widget_compromisso_titulo, item.titulo)
                setTextViewText(R.id.widget_compromisso_quando, item.quando(dia))
                setOnClickPendingIntent(R.id.widget_compromisso, abrir(context, item.link))
            }
        }
    }

    private fun fundoDe(status: StatusDia) = when (status) {
        StatusDia.TRABALHO -> R.drawable.widget_fundo_trabalho
        StatusDia.FOLGA -> R.drawable.widget_fundo_folga
        StatusDia.FERIAS -> R.drawable.widget_fundo_ferias
    }

    private fun iconeDe(status: StatusDia, noturno: Boolean) = when {
        status == StatusDia.FERIAS -> R.drawable.ic_widget_ferias
        status.livre -> R.drawable.ic_widget_folga
        noturno -> R.drawable.ic_widget_noite
        else -> R.drawable.ic_widget_trabalho
    }
}

class WidgetSemana : WidgetFolgui() {
    override fun montar(context: Context, dados: DadosWidget.Pronto, tamanho: TamanhoWidget): RemoteViews {
        val folga = dados.voce.proximoDia(dados.diaHoje + 1, livre = true)
            .takeIf { tamanho.altura >= ALTURA_COM_CABECALHO }

        return RemoteViews(context.packageName, R.layout.widget_semana).apply {
            setOnClickPendingIntent(android.R.id.background, abrir(context, LINK_AGENDA))
            setViewVisibility(R.id.widget_folga, if (folga == null) View.GONE else View.VISIBLE)
            if (folga != null) setTextViewText(R.id.widget_folga, "Próxima folga · ${diaResumido(folga)}")
            removeAllViews(R.id.widget_dias)
            (dados.diaHoje until dados.diaHoje + DIAS_SEMANA_WIDGET).forEach { dia ->
                addView(R.id.widget_dias, viewsDia(context, dados, dia))
            }
        }
    }

    private fun viewsDia(context: Context, dados: DadosWidget.Pronto, dia: Int): RemoteViews {
        val status = dados.voce.status(dia)
        val comEvento = dia in dados.diasComEvento
        return RemoteViews(context.packageName, R.layout.widget_semana_dia).apply {
            setTextViewText(R.id.widget_dia_semana, diaSemanaCurto(dia))
            setTextViewText(R.id.widget_dia_numero, partes(dia).dia.toString())
            setInt(R.id.widget_dia_numero, "setBackgroundResource", circuloDe(status))
            setViewVisibility(R.id.widget_dia_anel, if (dia == dados.diaHoje) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_dia_ponto, if (comEvento) View.VISIBLE else View.INVISIBLE)
            setContentDescription(
                R.id.widget_dia,
                "${diaPorExtenso(dia)}: ${status.rotulo}" + if (comEvento) ", com compromisso" else ""
            )
            setOnClickPendingIntent(R.id.widget_dia, abrir(context, linkDia(dia)))
        }
    }

    private fun circuloDe(status: StatusDia) = when (status) {
        StatusDia.TRABALHO -> R.drawable.widget_dia_trabalho
        StatusDia.FOLGA -> R.drawable.widget_dia_folga
        StatusDia.FERIAS -> R.drawable.widget_dia_ferias
    }
}

class WidgetCompromissos : WidgetFolgui() {
    override fun montar(context: Context, dados: DadosWidget.Pronto, tamanho: TamanhoWidget): RemoteViews {
        // Linhas fixas em vez de lista rolável: dispensa o serviço que uma lista manteria ligado
        val itens = dados.itens.take(linhasNaLista(tamanho.altura))

        return RemoteViews(context.packageName, R.layout.widget_compromissos).apply {
            setOnClickPendingIntent(android.R.id.background, abrir(context, LINK_EVENTOS))
            setOnClickPendingIntent(R.id.widget_novo, abrir(context, LINK_NOVO_EVENTO))
            setViewVisibility(R.id.widget_vazio, if (itens.isEmpty()) View.VISIBLE else View.GONE)
            removeAllViews(R.id.widget_lista)
            itens.forEach { addView(R.id.widget_lista, viewsItem(context, it, dados.diaHoje)) }
        }
    }

    private fun viewsItem(context: Context, item: ItemWidget, diaHoje: Int) =
        RemoteViews(context.packageName, R.layout.widget_compromisso_item).apply {
            setTextViewText(R.id.widget_item_titulo, item.titulo)
            setTextViewText(R.id.widget_item_quando, item.quando(diaHoje))
            // A cor do grupo é só um ponto ao lado do nome; sem grupo, fica a do tema
            item.cor?.let { setInt(R.id.widget_item_ponto, "setColorFilter", it.argb.toInt()) }
            setOnClickPendingIntent(R.id.widget_item, abrir(context, item.link))
        }
}

private fun viewsAviso(context: Context, texto: String) =
    RemoteViews(context.packageName, R.layout.widget_aviso).apply {
        setTextViewText(R.id.widget_aviso_texto, texto)
        setOnClickPendingIntent(android.R.id.background, abrir(context, LINK_APP))
    }

/** O link diferencia um toque do outro: sem ele, todos usariam o mesmo PendingIntent. */
private fun abrir(context: Context, link: String): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse(link))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
