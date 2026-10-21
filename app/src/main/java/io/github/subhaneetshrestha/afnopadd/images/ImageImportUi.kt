package io.github.subhaneetshrestha.afnopadd.images

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Whether the "Add image" choice is showing; opened by `cmd:image-import`. */
class ImageImportState {
    var choosing by mutableStateOf(false)
}

object ImageImportTags {
    const val DIALOG = "image-import-dialog"
    fun source(source: ImageSource) = "image-import:${source::class.simpleName}"
}

/**
 * The "Add image" flow: a choice of photos, files or camera, the system picker for it, then the
 * import into [sheet] at [placement] (read when the picker returns, so it follows the camera).
 * Images that could not be added are reported in a toast; [onImported] gets the new elements.
 *
 * The import runs in [scope], which should outlive the screen (the app's scope): a 20-photo batch
 * takes seconds, and rotating meanwhile must not cancel it halfway. The sheet outlives a rotation
 * too (`EditorSessions`), so the images land in it either way.
 */
@Composable
fun ImageImportHost(
    state: ImageImportState,
    sheet: Sheet,
    placement: () -> Placement,
    onImported: (List<ElementId>) -> Unit,
    scope: CoroutineScope,
    importer: ImageImporter? = null,
) {
    val context = LocalContext.current.applicationContext
    val resources = LocalResources.current
    val activeImporter = importer ?: remember(context) { AndroidImageImporter(context.applicationContext) }
    val currentPlacement by rememberUpdatedState(placement)
    val currentOnImported by rememberUpdatedState(onImported)
    val picker = rememberImagePicker { uris ->
        scope.launch {
            val summary = activeImporter.import(uris, sheet, currentPlacement())
            if (summary.failed.isNotEmpty()) {
                val message = resources.getQuantityString(R.plurals.image_import_failed, summary.failed.size, summary.failed.size, summary.results.size)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            if (summary.imported.isNotEmpty()) currentOnImported(summary.imported.map { it.id })
        }
    }
    if (!state.choosing) return
    fun choose(source: ImageSource) {
        state.choosing = false
        picker.launch(source)
    }
    AlertDialog(
        onDismissRequest = { state.choosing = false },
        modifier = Modifier.testTag(ImageImportTags.DIALOG),
        title = { Text(stringResource(R.string.command_image_import)) },
        text = {
            Column {
                TextButton(onClick = { choose(ImageSource.PhotoPicker) }, modifier = Modifier.testTag(ImageImportTags.source(ImageSource.PhotoPicker))) {
                    Text(stringResource(R.string.image_import_from_photos))
                }
                TextButton(onClick = { choose(ImageSource.Documents) }, modifier = Modifier.testTag(ImageImportTags.source(ImageSource.Documents))) {
                    Text(stringResource(R.string.image_import_from_files))
                }
                TextButton(onClick = { choose(ImageSource.Camera) }, modifier = Modifier.testTag(ImageImportTags.source(ImageSource.Camera))) {
                    Text(stringResource(R.string.image_import_from_camera))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { state.choosing = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
}
