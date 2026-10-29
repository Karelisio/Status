package com.karelisio.status.pilote

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.karelisio.status.AppGraph
import com.karelisio.status.data.prefs.SettingsStore
import kotlinx.coroutines.runBlocking

/**
 * Read-only list of presets for Pilote: content://<applicationId>.presets/presets
 * Columns: _id, id, name, icon, active (0/1), built_in (0/1).
 */
class PresetsProvider : ContentProvider() {
    private lateinit var matcher: UriMatcher

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        AppGraph.init(ctx)
        matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI("${ctx.packageName}.presets", PiloteContract.PATH_PRESETS, 1)
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (matcher.match(uri) != 1) return null
        val (presets, active) = runBlocking {
            AppGraph.tweaks.loadPresets() to AppGraph.settings.get(SettingsStore.Keys.activePresetId)
        }
        val columns = arrayOf("_id", PiloteContract.COL_ID, PiloteContract.COL_NAME, PiloteContract.COL_ICON, PiloteContract.COL_ACTIVE, PiloteContract.COL_BUILT_IN)
        return MatrixCursor(columns, presets.size).apply {
            presets.forEachIndexed { i, p ->
                addRow(arrayOf<Any>(i.toLong(), p.id, p.name, p.icon, if (p.id == active) 1 else 0, if (p.builtIn) 1 else 0))
            }
        }
    }

    override fun getType(uri: Uri): String? =
        if (matcher.match(uri) == 1) "vnd.android.cursor.dir/vnd.${context?.packageName}.preset" else null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Lecture seule")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Lecture seule")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Lecture seule")
}
