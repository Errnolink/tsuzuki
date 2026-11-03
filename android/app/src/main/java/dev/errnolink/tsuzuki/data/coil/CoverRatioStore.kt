package dev.errnolink.tsuzuki.data.coil

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class CoverRatioStore(context: Context) : SQLiteOpenHelper(
    context,
    context.cacheDir.resolve("cover-ratios.db").absolutePath,
    null,
    1,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE ratios (manga_id INTEGER PRIMARY KEY, cover_key TEXT NOT NULL, ratio REAL NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ratios")
        onCreate(db)
    }

    fun load(): Map<Long, Pair<String, Float>> = buildMap {
        readableDatabase.rawQuery("SELECT manga_id, cover_key, ratio FROM ratios", null).use { cursor ->
            while (cursor.moveToNext()) {
                put(cursor.getLong(0), cursor.getString(1) to cursor.getFloat(2))
            }
        }
    }

    fun put(id: Long, key: String, ratio: Float) {
        writableDatabase.insertWithOnConflict(
            "ratios",
            null,
            ContentValues(3).apply {
                put("manga_id", id)
                put("cover_key", key)
                put("ratio", ratio)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }
}
