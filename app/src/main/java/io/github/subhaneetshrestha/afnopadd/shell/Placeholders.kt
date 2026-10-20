package io.github.subhaneetshrestha.afnopadd.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.shell.theme.AfnopaddTheme

/** Stand-in for the library screen until `sheet-library` lands (Task 13). */
@Composable
fun LibraryPlaceholder(onOpenEditor: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize().testTag(ShellTags.LIBRARY_SCREEN)) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.library_placeholder_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Button(
                onClick = onOpenEditor,
                modifier = Modifier.testTag(ShellTags.OPEN_EDITOR),
            ) {
                Text(text = stringResource(R.string.action_open_editor))
            }
        }
    }
}

/** Stand-in for the editor screen until the canvas and tools land (Task 11). */
@Composable
fun EditorPlaceholder(sheetId: String, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize().testTag(ShellTags.EDITOR_SCREEN)) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.editor_placeholder_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.editor_placeholder_sheet, sheetId),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun LibraryPlaceholderPreview() {
    AfnopaddTheme {
        LibraryPlaceholder(onOpenEditor = {})
    }
}

@PreviewLightDark
@Composable
private fun EditorPlaceholderPreview() {
    AfnopaddTheme {
        EditorPlaceholder(sheetId = "01JAX9Q8K0TZ3M4N5P6R7S8T9V")
    }
}
