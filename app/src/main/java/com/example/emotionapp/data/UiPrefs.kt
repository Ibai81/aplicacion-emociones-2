package com.example.emotionapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val PREFS_UI = "ui_prefs"
private const val KEY_SHOW_HINTS = "cfg_show_hints"

/**
 * Preferencias de UI con estado vivo (Flow).
 * - observeShowHints(context) -> flujo que notifica cambios en tiempo real.
 * - setShowHints(context, value) -> guarda y notifica.
 */
object UiPrefs {
    private var showHintsFlow: MutableStateFlow<Boolean>? = null

    fun observeShowHints(context: Context): StateFlow<Boolean> {
        showHintsFlow?.let { return it }
        val init = getShowHints(context)
        val flow = MutableStateFlow(init)
        showHintsFlow = flow
        return flow
    }

    fun getShowHints(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_SHOW_HINTS, true)
    }

    fun setShowHints(context: Context, value: Boolean) {
        val sp = context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_SHOW_HINTS, value).apply()
        val flow = showHintsFlow
        if (flow == null) showHintsFlow = MutableStateFlow(value)
        else flow.value = value
    }
}
