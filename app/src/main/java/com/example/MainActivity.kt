package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.TtsScreen
import com.example.ui.TtsViewModel
import com.example.ui.theme.TextToSpeechTheme

class MainActivity : ComponentActivity() {

  private val ttsViewModel: TtsViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      TextToSpeechTheme {
        TtsScreen(
          viewModel = ttsViewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}

