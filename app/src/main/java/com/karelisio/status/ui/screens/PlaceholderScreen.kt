package com.karelisio.status.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.karelisio.status.ui.components.ScreenScaffold

@Composable
fun PlaceholderScreen(title: String, onBack: (() -> Unit)? = null) {
    ScreenScaffold(title = title, onBack = onBack) {
        item { Text("Bientôt") }
    }
}
