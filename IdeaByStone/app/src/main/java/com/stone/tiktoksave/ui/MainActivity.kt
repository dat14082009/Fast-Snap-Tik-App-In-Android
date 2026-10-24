package com.stone.tiktoksave.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.stone.tiktoksave.App
import com.stone.tiktoksave.ui.theme.ThemedContent

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShareIntent(intent)

        val container = (application as App).container
        setContent {
            ThemedContent(container.settings) {
                AppNavigation(vm = viewModel, container = container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /** Chế độ A: link do ShareReceiverActivity chuyển sang. */
    private fun handleShareIntent(intent: Intent?) {
        val shared = intent?.getStringExtra(EXTRA_SHARED_URL) ?: return
        intent.removeExtra(EXTRA_SHARED_URL)
        viewModel.onSharedLink(shared)
    }

    companion object {
        const val EXTRA_SHARED_URL = "com.stone.tiktoksave.SHARED_URL"
    }
}
