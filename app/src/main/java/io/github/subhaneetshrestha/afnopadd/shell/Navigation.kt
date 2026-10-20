package io.github.subhaneetshrestha.afnopadd.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay

/**
 * The app's single back stack: Library at the root, Editor pushed on top. The stack is saveable,
 * so rotation and process death land the user back on the same destination.
 */
@Composable
fun AfnopaddNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Destination.Library)

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Destination.Library> {
                LibraryPlaceholder(
                    onOpenEditor = { backStack.add(Destination.Editor(sheetId = PLACEHOLDER_SHEET_ID)) },
                )
            }
            entry<Destination.Editor> { key ->
                Box(Modifier.fillMaxSize().testTag(ShellTags.EDITOR_SCREEN)) {
                    EditorScreen(sheetId = key.sheetId)
                }
            }
        },
    )
}

/** Until the library exists, the placeholder opens one fixed sheet id. */
private const val PLACEHOLDER_SHEET_ID = "placeholder"
