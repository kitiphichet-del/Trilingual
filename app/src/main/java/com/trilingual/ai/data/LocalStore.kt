package com.trilingual.ai.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Each completed phrase is committed to SQLite immediately, before translation begins. */
data class Meeting(val id: Long, val title: String, val mode: String, val createdAt: Long, val summary: String)
data class Phrase(
    val id: Long, val meetingId: Long, val speaker: Int, val sourceLanguage: String,
    val source: String, val thai: String, val english: String, val chinese: String, val createdAt: Long
)

class LocalStore(context: Context) : SQLiteOpenHelper(context, "trilingual.db", null, 1) {
    private val _meetings = MutableStateFlow(emptyList<Meeting>())
    val meetings = _meetings.asStateFlow()
    private val _phrases = MutableStateFlow(emptyList<Phrase>())
    val phrases = _phrases.asStateFlow()
    @Volatile private var visibleMeetingId = 0L

    @Synchronized fun selectMeeting(id: Long) {
        visibleMeetingId = id
        refreshPhrases(id)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE meetings(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, mode TEXT NOT NULL, created_at INTEGER NOT NULL, summary TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE phrases(id INTEGER PRIMARY KEY AUTOINCREMENT, meeting_id INTEGER NOT NULL, speaker INTEGER NOT NULL, lang TEXT NOT NULL, source TEXT NOT NULL, thai TEXT NOT NULL DEFAULT '', english TEXT NOT NULL DEFAULT '', chinese TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, FOREIGN KEY(meeting_id) REFERENCES meetings(id))")
        db.execSQL("CREATE INDEX phrases_by_meeting ON phrases(meeting_id, id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema migration must be added when database version changes. Never drop user notes.
    }

    @Synchronized fun newMeeting(mode: String): Long {
        val ts = System.currentTimeMillis()
        val title = (if (mode == "meeting") "การประชุม " else "บทสนทนา ") + java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
        val id = writableDatabase.compileStatement("INSERT INTO meetings(title,mode,created_at,summary) VALUES(?,?,?,?)").apply {
            bindString(1, title); bindString(2, mode); bindLong(3, ts); bindString(4, "")
        }.executeInsert()
        refreshSessions()
        return id
    }

    @Synchronized fun addPhrase(meetingId: Long, speaker: Int, lang: String, original: String): Long {
        val stmt = writableDatabase.compileStatement("INSERT INTO phrases(meeting_id,speaker,lang,source,created_at) VALUES(?,?,?,?,?)")
        stmt.bindLong(1, meetingId); stmt.bindLong(2, speaker.toLong()); stmt.bindString(3, lang)
        stmt.bindString(4, original); stmt.bindLong(5, System.currentTimeMillis())
        val id = stmt.executeInsert()
        if (visibleMeetingId == meetingId) refreshPhrases(meetingId)
        return id
    }

    @Synchronized fun updateTranslation(id: Long, thai: String, english: String, chinese: String, meetingId: Long) {
        writableDatabase.execSQL("UPDATE phrases SET thai=?,english=?,chinese=? WHERE id=?", arrayOf(thai, english, chinese, id))
        if (visibleMeetingId == meetingId) refreshPhrases(meetingId)
    }

    @Synchronized fun setSummary(meetingId: Long, summary: String) {
        writableDatabase.execSQL("UPDATE meetings SET summary=? WHERE id=?", arrayOf(summary, meetingId))
        refreshSessions()
    }

    /** Delete a session and all its transcript rows atomically; never leave orphan phrases. */
    @Synchronized fun deleteMeeting(meetingId: Long): Boolean {
        require(meetingId > 0L) { "Invalid meeting ID" }
        val db = writableDatabase
        db.beginTransaction()
        val deleted = try {
            val where = arrayOf(meetingId.toString())
            db.delete("phrases", "meeting_id=?", where)
            val removed = db.delete("meetings", "id=?", where) > 0
            db.setTransactionSuccessful()
            removed
        } finally {
            db.endTransaction()
        }
        if (visibleMeetingId == meetingId) {
            visibleMeetingId = 0L
            _phrases.value = emptyList()
        }
        refreshSessions()
        return deleted
    }

    @Synchronized fun refreshSessions() {
        val result = mutableListOf<Meeting>()
        readableDatabase.rawQuery("SELECT id,title,mode,created_at,summary FROM meetings ORDER BY id DESC LIMIT 150", null).use { cur ->
            while (cur.moveToNext()) result.add(Meeting(cur.getLong(0), cur.getString(1), cur.getString(2), cur.getLong(3), cur.getString(4)))
        }
        _meetings.value = result
    }

    @Synchronized fun refreshPhrases(meetingId: Long) {
        val result = mutableListOf<Phrase>()
        readableDatabase.rawQuery("SELECT id,meeting_id,speaker,lang,source,thai,english,chinese,created_at FROM phrases WHERE meeting_id=? ORDER BY id", arrayOf(meetingId.toString())).use { cur ->
            while (cur.moveToNext()) result.add(Phrase(cur.getLong(0), cur.getLong(1), cur.getInt(2), cur.getString(3), cur.getString(4), cur.getString(5), cur.getString(6), cur.getString(7), cur.getLong(8)))
        }
        _phrases.value = result
    }
}
