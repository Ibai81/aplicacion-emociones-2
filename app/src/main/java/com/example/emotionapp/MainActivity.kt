@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.emotionapp

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Pantallas
import com.example.emotionapp.ui.audio.VoiceLogScreen
import com.example.emotionapp.ui.emociones.EmotionScreen
import com.example.emotionapp.ui.reflexion.ReflexionScreen
import com.example.emotionapp.ui.gestor.GestorScreen
import com.example.emotionapp.ui.info.InfoScreen
import com.example.emotionapp.ui.configuracion.ConfiguracionScreen
import com.example.emotionapp.ui.secundarias.SecundariasScreen

// Bridge para abrir Emociones desde Audio
import com.example.emotionapp.data.consumePendingOpenEmotion

/* =========================================================
   Definiciones compartidas
   ========================================================= */
data class EmotionDef(val key: String, val label: String)

val defaultEmotionPalette: List<EmotionDef> = listOf(
    // PRIMARIAS (sin culpa ni vergüenza, que pasan a secundarias)
    EmotionDef("miedo",     "Miedo"),
    EmotionDef("ira",       "Ira"),
    EmotionDef("tristeza",  "Tristeza"),
    EmotionDef("alegria",   "Alegría"),
    EmotionDef("asco",      "Asco"),
    EmotionDef("sorpresa",  "Sorpresa"),
)

/* ================== Activity ================== */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

private enum class Screen(val title: String) {
    Rapida("Rápida"),
    Emociones("Emociones"),
    Primarias("Primarias"),         // <- antes “Info”
    Secundarias("Secundarias"),     // <- va justo después de Primarias
    Reflexion("Reflexión"),
    Gestor("Gestor"),
    Config("Config")
}

@Composable
private fun AppRoot() {
    var primaryColor by remember { mutableStateOf(Color(0xFF1E88E5)) }
    val emotionColors: SnapshotStateMap<String, Color> = remember { mutableStateMapOf() }

    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = primaryColor) else lightColorScheme(primary = primaryColor)

    // Status bar
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = scheme.primary.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
    }

    MaterialTheme(colorScheme = scheme) {
        // Orden de pestañas (Primarias y Secundarias juntas)
        val tabs = remember {
            listOf(
                Screen.Rapida,
                Screen.Emociones,
                Screen.Primarias,
                Screen.Secundarias,
                Screen.Reflexion,
                Screen.Gestor,
                Screen.Config
            )
        }
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(initialPage = 1, pageCount = { tabs.size })

        // Señal externa (desde Audio) para ir a Emociones
        LaunchedEffect(Unit) {
            delay(80)
            val ctx = view.context
            if (consumePendingOpenEmotion(ctx)) {
                val idx = tabs.indexOf(Screen.Emociones)
                if (idx >= 0) pagerState.animateScrollToPage(idx)
            }
        }

        // Color por emoción
        val getEmotionColor: (String) -> Color = { key ->
            emotionColors[key] ?: when (key) {
                "miedo" -> Color(0xFF64B5F6)
                "ira" -> Color(0xFFE57373)
                "tristeza" -> Color(0xFF90CAF9)
                "alegria" -> Color(0xFFFFD54F)
                "asco" -> Color(0xFF81C784)
                "sorpresa" -> Color(0xFFFFB74D)
                else -> scheme.primary
            }
        }

        Scaffold(topBar = { TopAppBar(title = { Text("Emociones") }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 8.dp
                ) {
                    tabs.forEachIndexed { index, s ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(s.title) }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (tabs[page]) {
                        Screen.Rapida -> VoiceLogScreen()
                        Screen.Emociones -> EmotionScreen(getEmotionColor = getEmotionColor)
                        Screen.Primarias -> InfoScreen()          // mismo composable, cambia solo el nombre de pestaña
                        Screen.Secundarias -> SecundariasScreen()
                        Screen.Reflexion -> ReflexionScreen()
                        Screen.Gestor -> GestorScreen()
                        Screen.Config -> ConfiguracionScreen(
                            primaryColor = primaryColor,
                            onColorSelected = { c -> primaryColor = c },
                            emotionColors = emotionColors,
                            onResetAll = { emotionColors.clear() }
                        )
                    }
                }
            }
        }
    }
}
