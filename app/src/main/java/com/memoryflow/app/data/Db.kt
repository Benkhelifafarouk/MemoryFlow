package com.memoryflow.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite storage with versioned migrations.
 *
 * The schema is intentionally explicit (no annotation processor) which keeps the build
 * simple and deterministic while still giving typed access through [Repository].
 */
class Db(context: Context, name: String = DB_NAME) : SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE decks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT '',
                is_active INTEGER NOT NULL DEFAULT 0,
                color_index INTEGER NOT NULL DEFAULT 0,
                daily_limit INTEGER NOT NULL DEFAULT 20,
                new_per_day INTEGER NOT NULL DEFAULT 10,
                notifications_enabled INTEGER NOT NULL DEFAULT 1,
                archived INTEGER NOT NULL DEFAULT 0,
                sort_order INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE cards (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                deck_id INTEGER NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
                front TEXT NOT NULL,
                back TEXT NOT NULL,
                alt_answers TEXT NOT NULL DEFAULT '',
                hint1 TEXT NOT NULL DEFAULT '',
                hint2 TEXT NOT NULL DEFAULT '',
                explanation TEXT NOT NULL DEFAULT '',
                type TEXT NOT NULL DEFAULT 'VOCABULARY',
                tags TEXT NOT NULL DEFAULT '',
                state INTEGER NOT NULL DEFAULT 0,
                due INTEGER NOT NULL DEFAULT 0,
                stability REAL NOT NULL DEFAULT 0,
                difficulty REAL NOT NULL DEFAULT 0,
                reps INTEGER NOT NULL DEFAULT 0,
                lapses INTEGER NOT NULL DEFAULT 0,
                last_review INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                suspended INTEGER NOT NULL DEFAULT 0,
                weak_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_cards_deck_due ON cards(deck_id, due)")
        db.execSQL("CREATE INDEX idx_cards_deck_state ON cards(deck_id, state)")
        db.execSQL(
            """
            CREATE TABLE reviews (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_id INTEGER NOT NULL,
                deck_id INTEGER NOT NULL,
                rating INTEGER NOT NULL,
                answer_text TEXT NOT NULL DEFAULT '',
                was_correct INTEGER NOT NULL DEFAULT 0,
                hint_count INTEGER NOT NULL DEFAULT 0,
                reviewed_at INTEGER NOT NULL,
                elapsed_days REAL NOT NULL DEFAULT 0,
                scheduled_days INTEGER NOT NULL DEFAULT 0,
                stability_after REAL NOT NULL DEFAULT 0,
                difficulty_after REAL NOT NULL DEFAULT 0,
                was_new INTEGER NOT NULL DEFAULT 0,
                is_practice INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_reviews_at ON reviews(reviewed_at)")
        db.execSQL("CREATE INDEX idx_reviews_deck ON reviews(deck_id, reviewed_at)")
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_id INTEGER NOT NULL,
                deck_id INTEGER NOT NULL,
                kind TEXT NOT NULL DEFAULT 'SCHEDULED',
                hint_count INTEGER NOT NULL DEFAULT 0,
                answered INTEGER NOT NULL DEFAULT 0,
                correct INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                answered_at INTEGER NOT NULL DEFAULT 0,
                notification_id INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_id INTEGER NOT NULL,
                kind TEXT NOT NULL DEFAULT 'SCHEDULED',
                at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_notifications_at ON notifications(at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var version = oldVersion
        while (version < newVersion) {
            when (version) {
                // v1 -> v2: weak-word tracking ("I don't know" / failed answers).
                1 -> db.execSQL("ALTER TABLE cards ADD COLUMN weak_count INTEGER NOT NULL DEFAULT 0")
                else -> Unit
            }
            version++
        }
    }

    companion object {
        const val DB_NAME = "memoryflow.db"
        const val VERSION = 2
    }
}

// ---------------------------------------------------------------------------
// Small cursor helpers used by Repository (avoid repeated boilerplate).
// ---------------------------------------------------------------------------

internal fun Cursor.str(name: String): String = getString(getColumnIndexOrThrow(name)) ?: ""

internal fun Cursor.strOr(default: String, name: String): String {
    val idx = getColumnIndexOrThrow(name)
    return if (isNull(idx)) default else (getString(idx) ?: default)
}

internal fun Cursor.lng(name: String): Long = getLong(getColumnIndexOrThrow(name))

internal fun Cursor.dbl(name: String): Double = getDouble(getColumnIndexOrThrow(name))

internal fun Cursor.int(name: String): Int = getInt(getColumnIndexOrThrow(name))

internal fun Cursor.bool(name: String): Boolean = int(name) != 0

internal fun ContentValues.putBool(key: String, value: Boolean) = put(key, if (value) 1 else 0)
