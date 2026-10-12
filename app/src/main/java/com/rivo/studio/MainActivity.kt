package com.rivo.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat
import com.rivo.studio.ui.theme.RivoTheme

class MainActivity : ComponentActivity() {
    private lateinit var studioViewModel: StudioViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        studioViewModel = ViewModelProvider(this)[StudioViewModel::class.java]
        setContent {
            val state by studioViewModel.state.collectAsState()
            RivoTheme(darkTheme = state.darkTheme) {
                SideEffect {
                    val surface = if (state.darkTheme) android.graphics.Color.rgb(16, 26, 25) else android.graphics.Color.rgb(245, 248, 246)
                    window.statusBarColor = surface
                    window.navigationBarColor = surface
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !state.darkTheme
                        isAppearanceLightNavigationBars = !state.darkTheme
                    }
                }
                RivoStudioApp(studioViewModel, state)
            }
        }
    }
}
