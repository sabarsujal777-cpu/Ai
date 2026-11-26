package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.firebase.configureAppCheck
import com.example.ui.KavyaApp
import com.example.ui.theme.KavyaTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Configure Firebase App Check with launch intent before rendering UI or Firebase operations
    configureAppCheck(this, intent)

    enableEdgeToEdge()
    setContent {
      KavyaTheme {
        KavyaApp()
      }
    }
  }
}
