package com.example.emotionapp

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.emotionapp.ui.configuracion.SettingsScreen
import com.example.emotionapp.ui.emociones.EmotionScreen
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.view.WindowCompat

/* ===== Modelo base accesible desde las pantallas ===== */

data class EmotionDef(
    val key: String,
    val label: String,
    val color: Color
)

// Paleta por defecto
val defaultEmotionPalette = listOf(
    EmotionDef("miedo", "Miedo", Color(0xFFEF5350)),
    EmotionDef("ira", "Ira", Color(0xFFD81B60)),
    EmotionDef("vergüenza", "Vergüenza", Color(0xFF8E24AA)),
    EmotionDef("desprecio", "Desprecio", Color(0xFF5E35B1)),
    EmotionDef("asco", "Asco", Color(0xFF3949AB)),
    EmotionDef("culpa", "Culpa", Color(0xFF1E88E5)),
    EmotionDef("sufrimiento", "Sufrimiento", Color(0xFF039BE5)),
    EmotionDef("interes", "Interés", Color(0xFF00ACC1)),
    EmotionDef("sorpresa", "Sorpresa", Color(0xFF43A047)),
    EmotionDef("alegria", "Alegría", Color(0xFFFDD835))
)

/* ===== Theme sencillo con color primario configurable ===== */

@Composable
private fun AppTheme(primary: Color, content: @Composable () -> Unit) {
    val base = lightColorScheme()
    val scheme = base.copy(
        primary = primary,
        secondary = primary,
        tertiary = primary,
        primaryContainer = primary.copy(alpha = 0.15f)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/* ===== Activity ===== */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current

            // Estado persistente: color primario y colores por emoción
            var primaryColor by remember { mutableStateOf(loadPrimaryColor(context)) }
            var emotionColors by remember { mutableStateOf(loadEmotionColors(context)) }

            AppTheme(primary = primaryColor) {
                ApplySystemBars(primaryColor)   // <- añade esta línea
                AppRoot(
                    primaryColor = primaryColor,
                    onPrimaryChange = { c ->
                        primaryColor = c
                        savePrimaryColor(context, c)
                    },
                    emotionColors = emotionColors,
                    onEmotionColorChange = { key, c ->
                        emotionColors = emotionColors.toMutableMap().apply { put(key, c) }
                        saveEmotionColors(context, emotionColors)
                    },
                    onResetAllEmotionColors = {
                        emotionColors = emptyMap()
                        saveEmotionColors(context, emotionColors)
                    }
                )
            }
        }
    }
}

/* ===== Tabs: Emociones | Configuración ===== */

private enum class Screen { Emociones, Configuracion }

@Composable
private fun AppRoot(
    primaryColor: Color,
    onPrimaryChange: (Color) -> Unit,
    emotionColors: Map<String, Color>,
    onEmotionColorChange: (String, Color) -> Unit,
    onResetAllEmotionColors: () -> Unit
) {
    var current by rememberSaveable { mutableStateOf(Screen.Emociones) }
    val tabs = listOf(Screen.Emociones, Screen.Configuracion)
    val selectedIndex = tabs.indexOf(current)
    Column {
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            tabs.forEach { scr ->
                Tab(
                    selected = current == scr,
                    onClick = { current = scr },
                    text = { Text(if (scr == Screen.Emociones) "Emociones" else "Configuración") }
                )
            }
        }

        when (current) {
            Screen.Emociones -> EmotionScreen(
                // color por emoción: primero preferencia, si no, por defecto
                getEmotionColor = { key ->
                    emotionColors[key]
                        ?: defaultEmotionPalette.firstOrNull { it.key == key }?.color
                        ?: Color(0xFF1F2937)
                }
            )
            Screen.Configuracion -> SettingsScreen(
                primaryColor = primaryColor,
                onPickPrimary = onPrimaryChange,
                palette = presetPalette(),
                // bloque de colores por emoción
                defaultPalette = defaultEmotionPalette,
                emotionColors = emotionColors,
                onPickForEmotion = onEmotionColorChange,
                onResetAll = onResetAllEmotionColors
            )
        }
    }
}

/* ===== Persistencia de preferencias UI (color app + colores emoción) ===== */

private const val PREFS_UI = "ui_prefs"
private const val KEY_PRIMARY_COLOR = "primary_color_argb"
private const val KEY_EMOTION_COLORS = "emotion_colors_json"

private fun savePrimaryColor(context: android.content.Context, color: Color) {
    val prefs = context.getSharedPreferences(PREFS_UI, android.content.Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_PRIMARY_COLOR, color.toArgb()).apply()
}

private fun loadPrimaryColor(context: android.content.Context): Color {
    val prefs = context.getSharedPreferences(PREFS_UI, android.content.Context.MODE_PRIVATE)
    val def = Color(0xFF6A1B9A) // morado por defecto
    val argb = prefs.getInt(KEY_PRIMARY_COLOR, def.toArgb())
    return Color(argb)
}

private fun saveEmotionColors(context: android.content.Context, map: Map<String, Color>) {
    val prefs = context.getSharedPreferences(PREFS_UI, android.content.Context.MODE_PRIVATE)
    val gson = Gson()
    val plain = map.mapValues { it.value.toArgb() } // key -> Int
    prefs.edit().putString(KEY_EMOTION_COLORS, gson.toJson(plain)).apply()
}

private fun loadEmotionColors(context: android.content.Context): Map<String, Color> {
    val prefs = context.getSharedPreferences(PREFS_UI, android.content.Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_EMOTION_COLORS, null) ?: return emptyMap()
    val type = object : TypeToken<Map<String, Int>>() {}.type
    val loaded: Map<String, Int> = Gson().fromJson(json, type)
    return loaded.mapValues { Color(it.value) }
}
@Composable
private fun ApplySystemBars(color: Color) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Colores de barras
            window.statusBarColor = color.toArgb()
            window.navigationBarColor = color.toArgb()

            // Iconos oscuros o claros según el color de fondo
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val useDarkIcons = color.luminance() > 0.5f
            controller.isAppearanceLightStatusBars = useDarkIcons
            controller.isAppearanceLightNavigationBars = useDarkIcons
        }
    }
}


/* ===== Paleta sugerida para Configuración ===== */

fun presetPalette(): List<Color> = listOf(
    Color(0xFF6A1B9A), // morado (por defecto)
    Color(0xFF7C4DFF),
    Color(0xFF512DA8),
    Color(0xFF3949AB),
    Color(0xFF1E88E5),
    Color(0xFF00ACC1),
    Color(0xFF43A047),
    Color(0xFF8BC34A),
    Color(0xFFFFC107),
    Color(0xFFFF9800),
    Color(0xFFF4511E),
    Color(0xFFE91E63),
    Color(0xFF546E7A),
    Color(0xFF1F2937)
)
