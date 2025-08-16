@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.emotionapp

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ===== Paleta por defecto local (para colores de emociones) ===== */
data class EmotionDef(val key: String, val label: String, val color: Color)
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

/* ===== Tabs ===== */
enum class Screen { Rapida, Emociones, Reflexion, Gestor, Info, Configuracion }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

@Composable
private fun AppRoot() {
    // Estado de colores (se usa en Configuración)
    var primaryColor by remember { mutableStateOf(Color(0xFF6A1B9A)) }
    val emotionColors: SnapshotStateMap<String, Color> = remember { mutableStateMapOf() }

    // ======= Tema dinámico a partir de primaryColor =======
    val dark = isSystemInDarkTheme()
    val baseScheme = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = baseScheme.copy(primary = primaryColor)

    // Actualiza la barra de estado (hora/batería) al color primario
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.primary.toArgb()
            // Iconos oscuros si el fondo es claro
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = scheme.primary.luminance() > 0.5f
        }
    }

    // Paleta simple para Configuración
    val uiPalette = remember {
        listOf(
            Color(0xFF6A1B9A), Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF00ACC1),
            Color(0xFF43A047), Color(0xFFF4511E), Color(0xFFFB8C00), Color(0xFFFDD835),
            Color(0xFF546E7A), Color(0xFF8D6E63)
        )
    }

    // Función de color para las barras de emoción
    val getEmotionColor: (String) -> Color = remember(defaultEmotionPalette, emotionColors) {
        { key ->
            emotionColors[key]
                ?: defaultEmotionPalette.firstOrNull { it.key == key }?.color
                ?: Color(0xFF1F2937)
        }
    }

    val tabs = remember {
        listOf(
            Screen.Rapida to "Rápida",
            Screen.Emociones to "Emociones",
            Screen.Reflexion to "Reflexión",
            Screen.Gestor to "Gestor",
            Screen.Info to "Info",
            Screen.Configuracion to "Config"
        )
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { tabs.size }
    )
    val scope = rememberCoroutineScope()

    // Señal desde Gestor: abrir Emociones cuando haya un "pending open"
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            if (com.example.emotionapp.data.consumePendingOpenEmotion(ctx)) {
                val idx = tabs.indexOfFirst { it.first == Screen.Emociones }.coerceAtLeast(0)
                scope.launch { pagerState.animateScrollToPage(idx) }
            }
            delay(150)
        }
    }

    MaterialTheme(colorScheme = scheme) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Aplicación emociones") })

            // ====== TabRow desplazable ======
            ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, pair ->
                    val label = pair.second
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                label,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            // ====== Contenido con swipe lateral ======
            Surface(tonalElevation = 0.dp, modifier = Modifier.weight(1f)) {
                HorizontalPager(state = pagerState) { page ->
                    when (tabs[page].first) {
                        Screen.Rapida -> com.example.emotionapp.ui.audio.VoiceLogScreen()
                        Screen.Emociones -> com.example.emotionapp.ui.emociones.EmotionScreen(
                            getEmotionColor = getEmotionColor
                        )
                        Screen.Reflexion -> com.example.emotionapp.ui.reflexion.ReflexionScreen()
                        Screen.Gestor -> com.example.emotionapp.ui.gestor.GestorScreen()
                        Screen.Info -> com.example.emotionapp.ui.info.InfoScreen()
                        Screen.Configuracion -> com.example.emotionapp.ui.configuracion.SettingsScreen(
                            primaryColor = primaryColor,
                            onPickPrimary = { picked -> primaryColor = picked },
                            palette = uiPalette,
                            defaultPalette = defaultEmotionPalette,
                            emotionColors = emotionColors,
                            onPickForEmotion = { key, color -> emotionColors[key] = color },
                            onResetAll = {
                                primaryColor = Color(0xFF6A1B9A)
                                emotionColors.clear()
                            }
                        )
                    }
                }
            }
        }
    }
}

/* Paleta de ejemplo para Configuración (si la necesitas fuera) */
fun presetPalette(): List<Color> = listOf(
    Color(0xFF6A1B9A), Color(0xFF7C4DFF), Color(0xFF512DA8), Color(0xFF3949AB),
    Color(0xFF1E88E5), Color(0xFF00ACC1), Color(0xFF43A047), Color(0xFF8BC34A),
    Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFF4511E), Color(0xFFE91E63),
    Color(0xFF546E7A), Color(0xFF1F2937)
)
