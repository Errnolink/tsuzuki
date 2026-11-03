// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package dev.errnolink.tsuzuki.mangadex

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MangaMappings(context: Context) {

    private val dbMappings: SQLiteDatabase by lazy { openDatabase(context, DB_NAME) }

    fun getMangadexUUID(id: String, service: String): String? {
        if (service !in SERVICES) return null
        return getResult("SELECT mdex FROM mappings WHERE $service = ? LIMIT 1", arrayOf(id))
    }

    fun getExternalID(id: String, service: String): String? {
        if (service !in SERVICES) return null
        return getResult("SELECT $service FROM mappings WHERE mdex = ? LIMIT 1", arrayOf(id))
    }
    private fun getResult(queryString: String, whereArgs: Array<String>): String? {
        if (!dbMappings.isOpen) return null
        val cursor = dbMappings.rawQuery(queryString, whereArgs) ?: return null
        return cursor.use {
            if (it.moveToFirst()) it.getString(0)?.ifBlank { null } else null
        }
    }

    @Throws(IOException::class)
    private fun openDatabase(context: Context, dbPath: String): SQLiteDatabase {
        val dbFile: File = context.getDatabasePath(dbPath)
        if (!dbFile.exists()) {
            try {
                copyDatabase(context, dbFile, dbPath)
            } catch (e: IOException) {
                throw RuntimeException("Error creating source database", e)
            }
        }
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    @Throws(IOException::class)
    private fun copyDatabase(context: Context, dbFile: File, dbPath: String) {
        context.assets.open(dbPath).use { inputStream ->
            FileOutputStream(dbFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    companion object {
        const val DB_NAME = "2026-07-23_neko_mapping.db"
        val SERVICES = setOf("al", "mal", "mu_new", "mb")

        @Volatile
        private var cached: MangaMappings? = null

        fun get(context: Context): MangaMappings =
            cached ?: synchronized(this) {
                cached ?: MangaMappings(context.applicationContext).also { cached = it }
            }
    }
}
