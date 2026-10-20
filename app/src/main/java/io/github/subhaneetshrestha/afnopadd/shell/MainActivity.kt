package io.github.subhaneetshrestha.afnopadd.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import io.github.subhaneetshrestha.afnopadd.shell.theme.AfnopaddTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AfnopaddApp).container
        setContent {
            AfnopaddTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    AfnopaddNavigation()
                }
            }
        }
    }
}
