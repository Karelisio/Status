package com.karelisio.status.pilote

/** Public contract for Pilote (same signing key required). Mirrored in the README. */
object PiloteContract {
    const val ACTION_APPLY_PRESET = "com.karelisio.status.action.APPLY_PRESET"
    /** Preset id or name (case-insensitive). "restore" puts back the state saved before the active preset. */
    const val EXTRA_PRESET = "preset"
    const val PRESET_RESTORE = "restore"

    const val PATH_PRESETS = "presets"
    const val COL_ID = "id"
    const val COL_NAME = "name"
    const val COL_ICON = "icon"
    const val COL_ACTIVE = "active"
    const val COL_BUILT_IN = "built_in"

    const val RESULT_OK = 0
    const val RESULT_UNKNOWN_PRESET = 1
    const val RESULT_FAILED = 2
}
