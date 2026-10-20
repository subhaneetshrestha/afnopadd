package io.github.subhaneetshrestha.afnopadd.shell

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.library.LibraryScreen
import kotlinx.coroutines.launch

/** Connects the stateless [LibraryScreen] to the app's sheet library. */
@Composable
fun LibraryRoute(onOpenSheet: (SheetId) -> Unit, modifier: Modifier = Modifier) {
    val library = LocalAppContainer.current.library
    val sheets by library.sheets.collectAsStateWithLifecycle(initialValue = emptyList())
    val lastOpened by library.lastOpened.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    fun act(label: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "$label failed", e)
            } catch (e: java.io.IOException) {
                Log.w(TAG, "$label failed", e)
            }
        }
    }

    Box(modifier.fillMaxSize().testTag(ShellTags.LIBRARY_SCREEN)) {
        LibraryScreen(
            sheets = sheets,
            lastOpened = lastOpened,
            onNewSheet = { act("create") { onOpenSheet(library.createSheet().id) } },
            onOpenSheet = onOpenSheet,
            onRename = { id, title -> act("rename") { library.rename(id, title) } },
            onDuplicate = { id -> act("duplicate") { library.duplicate(id) } },
            onDelete = { id -> act("delete") { library.delete(id) } },
            onRestore = { id -> act("restore") { library.restore(id) } },
        )
    }
}

private const val TAG = "LibraryRoute"
