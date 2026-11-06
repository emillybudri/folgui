package com.example.folgui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Persistência de Dados com SQLite
class BancoHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        // Escalas das pessoas com quem o usuário quer combinar as folgas.
        // "padrao" guarda o ciclo dia a dia ("420-1140;F"); as colunas de turno são do formato antigo.
        db.execSQL(
            """
            CREATE TABLE pessoa (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome TEXT NOT NULL,
                tipo_escala TEXT NOT NULL,
                inicio_ciclo TEXT NOT NULL,
                ferias_inicio TEXT NOT NULL DEFAULT '',
                ferias_fim TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '',
                dias_semana INTEGER NOT NULL DEFAULT $SEG_A_SEX,
                turno_inicio INTEGER NOT NULL DEFAULT 480,
                turno_fim INTEGER NOT NULL DEFAULT 1020,
                padrao TEXT NOT NULL DEFAULT '',
                descanso_min INTEGER NOT NULL DEFAULT 0,
                atividades TEXT NOT NULL DEFAULT '',
                amigo_uid TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        // Eventos marcados nas folgas, com antecedência do lembrete e quem vai
        db.execSQL(
            """
            CREATE TABLE evento (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                titulo TEXT NOT NULL,
                data TEXT NOT NULL,
                horario TEXT NOT NULL,
                local_evento TEXT NOT NULL,
                antecedencia_min INTEGER NOT NULL,
                duracao_min INTEGER NOT NULL DEFAULT 120,
                participantes TEXT NOT NULL DEFAULT '*',
                time_millis INTEGER NOT NULL,
                agenda_id INTEGER NOT NULL DEFAULT 0,
                grupo_id INTEGER NOT NULL DEFAULT 0,
                avisos_extras TEXT NOT NULL DEFAULT '',
                detalhes TEXT NOT NULL DEFAULT '',
                aviso_na_agenda INTEGER NOT NULL DEFAULT 0,
                fora_da_agenda INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        criarTabelaExcecao(db)
        criarTabelaGrupo(db)
    }

    // Grupos de compromissos. A cor é o colorId da Google Agenda (ver CorGrupo)
    private fun criarTabelaGrupo(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE grupo (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome TEXT NOT NULL,
                cor INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    // Mudanças pontuais na escala: trocas de plantão, folgas e trabalhos extras (pessoa -1 = você)
    private fun criarTabelaExcecao(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE excecao (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pessoa_id INTEGER NOT NULL,
                data TEXT NOT NULL,
                tipo TEXT NOT NULL,
                turno TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versão 2: e-mail da pessoa para os convites (mantém os dados já cadastrados)
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE pessoa ADD COLUMN email TEXT NOT NULL DEFAULT ''")
        }
        // Versão 3: horário do turno, dias fixos da semana e duração dos eventos
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE pessoa ADD COLUMN dias_semana INTEGER NOT NULL DEFAULT $SEG_A_SEX")
            db.execSQL("ALTER TABLE pessoa ADD COLUMN turno_inicio INTEGER NOT NULL DEFAULT 480")
            db.execSQL("ALTER TABLE pessoa ADD COLUMN turno_fim INTEGER NOT NULL DEFAULT 1020")
            db.execSQL("ALTER TABLE evento ADD COLUMN duracao_min INTEGER NOT NULL DEFAULT 120")
            db.execSQL("UPDATE pessoa SET turno_inicio = 420, turno_fim = 1140 WHERE tipo_escala IN ('12x36', '2x2')")
            db.execSQL("UPDATE pessoa SET turno_inicio = 420, turno_fim = 420 WHERE tipo_escala = '24x48'")

            // A antiga escala 5x2 vira "dias fixos" com os mesmos cinco dias da semana
            db.rawQuery("SELECT id, inicio_ciclo FROM pessoa WHERE tipo_escala = '5x2'", null).use { c ->
                while (c.moveToNext()) {
                    val inicio = textoParaDia(c.getString(1) ?: "") ?: continue
                    db.execSQL(
                        "UPDATE pessoa SET tipo_escala = ?, dias_semana = ? WHERE id = ?",
                        arrayOf<Any>(TipoEscala.SEMANAL.codigo, diasSemanaDe5x2(inicio), c.getInt(0))
                    )
                }
            }
        }
        // Versão 4: ciclo dia a dia (escala personalizada), descanso, exceções e participantes.
        // O ciclo das pessoas antigas é montado na leitura a partir das colunas da versão 3.
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE pessoa ADD COLUMN padrao TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE pessoa ADD COLUMN descanso_min INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE evento ADD COLUMN participantes TEXT NOT NULL DEFAULT '*'")
            criarTabelaExcecao(db)
        }
        // Versão 5: id do evento na Google Agenda, para editar e excluir o mesmo evento lá
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE evento ADD COLUMN agenda_id INTEGER NOT NULL DEFAULT 0")
        }
        // Versão 6: segundo emprego, faculdade e outras atividades da pessoa
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE pessoa ADD COLUMN atividades TEXT NOT NULL DEFAULT ''")
        }
        // Versão 7: amigos conectados pelo código, que têm a escala vinda da conta deles
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE pessoa ADD COLUMN amigo_uid TEXT NOT NULL DEFAULT ''")
        }
        // Versão 8: grupos, vários lembretes por evento e situação do evento na Google Agenda
        if (oldVersion < 8) {
            criarTabelaGrupo(db)
            db.execSQL("ALTER TABLE evento ADD COLUMN grupo_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE evento ADD COLUMN avisos_extras TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE evento ADD COLUMN detalhes TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE evento ADD COLUMN aviso_na_agenda INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE evento ADD COLUMN fora_da_agenda INTEGER NOT NULL DEFAULT 0")
        }
    }

    companion object {
        private const val DATABASE_NAME = "folgui.db"
        private const val DATABASE_VERSION = 8
    }
}
