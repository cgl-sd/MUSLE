package com.musclejournal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.musclejournal.app.rendering.BodyArt
import com.musclejournal.app.ui.MuscleJournalApp
import com.musclejournal.app.ui.MuscleJournalTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val bodyArtLoader=Executors.newSingleThreadExecutor()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bodyArtLoader.execute {BodyArt.preload(applicationContext)}
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MuscleJournalTheme {
                MuscleJournalApp()
            }
        }
    }
    override fun onDestroy(){bodyArtLoader.shutdown();super.onDestroy()}
}
