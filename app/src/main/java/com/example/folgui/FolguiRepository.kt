package com.example.folgui

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

// Repositório SQLite das pessoas (escalas), das mudanças na escala e dos eventos
class FolguiRepository(context: Context) {

    private val helper = BancoHelper(context.applicationContext)

    // ---------- Pessoas ----------

    private fun valoresPessoa(pessoa: Pessoa) = ContentValues().apply {
        put("nome", pessoa.nome)
        put("tipo_escala", pessoa.escala.codigo)
        put("inicio_ciclo", diaParaTexto(pessoa.inicioCiclo))
        put("ferias_inicio", pessoa.feriasInicio?.let(::diaParaTexto) ?: "")
        put("ferias_fim", pessoa.feriasFim?.let(::diaParaTexto) ?: "")
        put("email", pessoa.email)
        put("padrao", padraoParaTexto(pessoa.padrao))
        put("descanso_min", pessoa.descansoMin)
        put("atividades", atividadesParaTexto(pessoa.atividades))
        put("amigo_uid", pessoa.amigoUid)
    }

    /** Retorna o id gerado (ou -1). Com [manterId], reaproveita o id (usado no "Desfazer"). */
    fun inserirPessoa(pessoa: Pessoa, manterId: Boolean = false): Int {
        val valores = valoresPessoa(pessoa).apply { if (manterId) put("id", pessoa.id) }
        return helper.writableDatabase.insert("pessoa", null, valores).toInt()
    }

    fun atualizarPessoa(pessoa: Pessoa): Boolean =
        helper.writableDatabase.update(
            "pessoa", valoresPessoa(pessoa), "id = ?", arrayOf(pessoa.id.toString())
        ) > 0

    /** Remove a pessoa e as mudanças de escala dela. */
    fun excluirPessoa(id: Int): Boolean {
        helper.writableDatabase.delete("excecao", "pessoa_id = ?", arrayOf(id.toString()))
        return helper.writableDatabase.delete("pessoa", "id = ?", arrayOf(id.toString())) > 0
    }

    fun listarPessoas(): List<Pessoa> {
        val excecoes = listarExcecoes()
        val lista = mutableListOf<Pessoa>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM pessoa ORDER BY nome COLLATE NOCASE ASC", null
        ).use { c ->
            while (c.moveToNext()) {
                val inicio = textoParaDia(c.texto("inicio_ciclo")) ?: continue
                val id = c.getInt(c.getColumnIndexOrThrow("id"))
                val escala = TipoEscala.porCodigo(c.texto("tipo_escala"))
                // Pessoas da versão 3 ainda não têm o ciclo gravado: monta a partir do turno e dos dias
                val padrao = textoParaPadrao(c.texto("padrao")) ?: padraoLegado(
                    escala,
                    c.getInt(c.getColumnIndexOrThrow("dias_semana")),
                    Turno(c.getInt(c.getColumnIndexOrThrow("turno_inicio")), c.getInt(c.getColumnIndexOrThrow("turno_fim")))
                )
                lista.add(
                    Pessoa(
                        id = id,
                        nome = c.texto("nome"),
                        escala = escala,
                        inicioCiclo = inicio,
                        padrao = padrao,
                        feriasInicio = textoParaDia(c.texto("ferias_inicio")),
                        feriasFim = textoParaDia(c.texto("ferias_fim")),
                        email = c.texto("email"),
                        descansoMin = c.getInt(c.getColumnIndexOrThrow("descanso_min")),
                        excecoes = excecoes[id].orEmpty(),
                        atividades = textoParaAtividades(c.texto("atividades")),
                        amigoUid = c.texto("amigo_uid")
                    )
                )
            }
        }
        return lista
    }

    // ---------- Mudanças na escala (exceções) ----------

    /** Grava a mudança do dia, substituindo a anterior se houver. */
    fun salvarExcecao(pessoaId: Int, excecao: Excecao) {
        removerExcecao(pessoaId, excecao.dia)
        helper.writableDatabase.insert("excecao", null, ContentValues().apply {
            put("pessoa_id", pessoaId)
            put("data", diaParaTexto(excecao.dia))
            put("tipo", excecao.tipo.codigo)
            put("turno", excecao.turno?.codigo ?: "")
        })
    }

    fun removerExcecao(pessoaId: Int, dia: Int) {
        helper.writableDatabase.delete(
            "excecao", "pessoa_id = ? AND data = ?", arrayOf(pessoaId.toString(), diaParaTexto(dia))
        )
    }

    /** Troca todas as mudanças da pessoa, usado quando as de um amigo chegam da conta dele. */
    fun substituirExcecoes(pessoaId: Int, excecoes: Map<Int, Excecao>) {
        helper.writableDatabase.delete("excecao", "pessoa_id = ?", arrayOf(pessoaId.toString()))
        excecoes.values.forEach { salvarExcecao(pessoaId, it) }
    }

    /** Mudanças de todas as pessoas, por id da pessoa e por dia. */
    fun listarExcecoes(): Map<Int, Map<Int, Excecao>> {
        val mapa = mutableMapOf<Int, MutableMap<Int, Excecao>>()
        helper.readableDatabase.rawQuery("SELECT * FROM excecao", null).use { c ->
            while (c.moveToNext()) {
                val dia = textoParaDia(c.texto("data")) ?: continue
                val tipo = TipoExcecao.porCodigo(c.texto("tipo")) ?: continue
                val turno = Turno.deCodigo(c.texto("turno"))
                if (tipo.trabalha && turno == null) continue
                mapa.getOrPut(c.getInt(c.getColumnIndexOrThrow("pessoa_id"))) { mutableMapOf() }[dia] =
                    Excecao(dia, tipo, turno.takeIf { tipo.trabalha })
            }
        }
        return mapa
    }

    // ---------- Eventos ----------

    private fun valoresEvento(evento: Evento) = ContentValues().apply {
        put("titulo", evento.titulo)
        put("data", diaParaTexto(evento.dia))
        put("horario", evento.horario)
        put("local_evento", evento.local)
        put("antecedencia_min", evento.antecedenciaMin)
        put("duracao_min", evento.duracaoMin)
        put("participantes", participantesParaTexto(evento.participantes))
        put("time_millis", evento.momento)
        put("grupo_id", evento.grupoId)
        put("avisos_extras", evento.avisosExtras.joinToString(","))
        put("detalhes", evento.detalhes)
    }

    /** Retorna o id gerado (ou -1 em caso de falha). Com [manterId], reaproveita o id. */
    fun inserirEvento(evento: Evento, manterId: Boolean = false): Int {
        val valores = valoresEvento(evento).apply { if (manterId) put("id", evento.id) }
        return helper.writableDatabase.insert("evento", null, valores).toInt()
    }

    fun atualizarEvento(evento: Evento): Boolean =
        helper.writableDatabase.update(
            "evento", valoresEvento(evento), "id = ?", arrayOf(evento.id.toString())
        ) > 0

    fun excluirEvento(id: Int): Boolean =
        helper.writableDatabase.delete("evento", "id = ?", arrayOf(id.toString())) > 0

    /** Id do evento na Google Agenda. Fica fora de [valoresEvento] para uma edição não apagar o vínculo. */
    fun agendaIdDe(id: Int): Long =
        helper.readableDatabase.rawQuery(
            "SELECT agenda_id FROM evento WHERE id = ?", arrayOf(id.toString())
        ).use { if (it.moveToFirst()) it.getLong(0) else 0 }

    fun salvarAgendaId(id: Int, agendaId: Long) {
        helper.writableDatabase.update(
            "evento", ContentValues().apply { put("agenda_id", agendaId) }, "id = ?", arrayOf(id.toString())
        )
    }

    /** Quem avisa do evento: a agenda (true) ou o alarme do Folgui. */
    fun marcarAvisoNaAgenda(id: Int, naAgenda: Boolean) {
        helper.writableDatabase.update(
            "evento", ContentValues().apply { put("aviso_na_agenda", if (naAgenda) 1 else 0) }, "id = ?", arrayOf(id.toString())
        )
    }

    fun marcarForaDaAgenda(id: Int, fora: Boolean) {
        helper.writableDatabase.update(
            "evento", ContentValues().apply { put("fora_da_agenda", if (fora) 1 else 0) }, "id = ?", arrayOf(id.toString())
        )
    }

    /** Muda o grupo de vários eventos de uma vez: ou todos mudam, ou nenhum. */
    fun moverParaGrupo(ids: Collection<Int>, grupoId: Int) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { id ->
                db.update("evento", ContentValues().apply { put("grupo_id", grupoId) }, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- Grupos ----------

    private fun valoresGrupo(grupo: Grupo) = ContentValues().apply {
        put("nome", grupo.nome)
        put("cor", grupo.cor.id)
    }

    /** Retorna o id gerado (ou -1). Com [manterId], reaproveita o id (usado no "Desfazer"). */
    fun inserirGrupo(grupo: Grupo, manterId: Boolean = false): Int {
        val valores = valoresGrupo(grupo).apply { if (manterId) put("id", grupo.id) }
        return helper.writableDatabase.insert("grupo", null, valores).toInt()
    }

    fun atualizarGrupo(grupo: Grupo): Boolean =
        helper.writableDatabase.update("grupo", valoresGrupo(grupo), "id = ?", arrayOf(grupo.id.toString())) > 0

    /** Exclui o grupo. Os eventos continuam, agora sem grupo. */
    fun excluirGrupo(id: Int) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.update("evento", ContentValues().apply { put("grupo_id", 0) }, "grupo_id = ?", arrayOf(id.toString()))
            db.delete("grupo", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun listarGrupos(): List<Grupo> {
        val lista = mutableListOf<Grupo>()
        helper.readableDatabase.rawQuery("SELECT * FROM grupo ORDER BY nome COLLATE NOCASE ASC", null).use { c ->
            while (c.moveToNext()) {
                val cor = CorGrupo.porId(c.getInt(c.getColumnIndexOrThrow("cor"))) ?: continue
                lista.add(Grupo(c.getInt(c.getColumnIndexOrThrow("id")), c.texto("nome"), cor))
            }
        }
        return lista
    }

    fun listarEventos(): List<Evento> {
        val lista = mutableListOf<Evento>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM evento ORDER BY time_millis ASC", null
        ).use { c ->
            while (c.moveToNext()) {
                val dia = textoParaDia(c.texto("data")) ?: continue
                lista.add(
                    Evento(
                        id = c.getInt(c.getColumnIndexOrThrow("id")),
                        titulo = c.texto("titulo"),
                        dia = dia,
                        horario = c.texto("horario"),
                        local = c.texto("local_evento"),
                        antecedenciaMin = c.getInt(c.getColumnIndexOrThrow("antecedencia_min")),
                        duracaoMin = c.getInt(c.getColumnIndexOrThrow("duracao_min")),
                        participantes = textoParaParticipantes(c.texto("participantes")),
                        agendaId = c.getLong(c.getColumnIndexOrThrow("agenda_id")),
                        grupoId = c.getInt(c.getColumnIndexOrThrow("grupo_id")),
                        avisosExtras = c.texto("avisos_extras").split(",").mapNotNull { it.toIntOrNull() },
                        detalhes = c.texto("detalhes"),
                        avisoNaAgenda = c.getInt(c.getColumnIndexOrThrow("aviso_na_agenda")) == 1,
                        foraDaAgenda = c.getInt(c.getColumnIndexOrThrow("fora_da_agenda")) == 1
                    )
                )
            }
        }
        return lista
    }

    /** Troca tudo pelo que veio da conta. Em transação: se algo falhar, nada se perde. */
    fun substituirTudo(
        pessoas: List<Pessoa>,
        excecoesVoce: Map<Int, Excecao>,
        eventos: List<Evento>,
        grupos: List<Grupo> = emptyList()
    ) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            listOf("excecao", "pessoa", "evento", "grupo").forEach { db.delete(it, null, null) }
            pessoas.forEach { p ->
                inserirPessoa(p, manterId = true)
                p.excecoes.values.forEach { salvarExcecao(p.id, it) }
            }
            excecoesVoce.values.forEach { salvarExcecao(Pessoa.ID_VOCE, it) }
            grupos.forEach { inserirGrupo(it, manterId = true) }
            eventos.forEach { inserirEvento(it, manterId = true) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.texto(coluna: String): String = getString(getColumnIndexOrThrow(coluna)) ?: ""
}
