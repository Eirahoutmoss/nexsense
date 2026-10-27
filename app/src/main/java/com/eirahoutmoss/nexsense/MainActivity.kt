package com.eirahoutmoss.nexsense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nexsense.ui.NexSenseRoot
import com.eirahoutmoss.nexsense.ui.theme.NexSenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NexSenseApp).container
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            NexSenseTheme(mode = settings.theme) {
                NexSenseRoot(container)
            }
        }
    }
}
