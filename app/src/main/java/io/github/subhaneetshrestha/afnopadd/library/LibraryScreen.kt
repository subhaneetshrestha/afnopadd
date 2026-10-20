package io.github.subhaneetshrestha.afnopadd.library

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import kotlinx.coroutines.launch
import java.io.File

object LibraryTags {
    const val SCREEN = "library:screen"
    const val GRID = "library:grid"
    const val NEW_SHEET = "library:new"
    const val CONTINUE = "library:continue"
    const val MENU_RENAME = "library:menu:rename"
    const val MENU_DUPLICATE = "library:menu:duplicate"
    const val MENU_DELETE = "library:menu:delete"
    const val RENAME_FIELD = "library:rename-field"
    const val DIALOG_CONFIRM = "library:dialog-confirm"
    const val UNDO = "library:undo"

    /** Grid items before the first sheet card (new, continue, header), for scrolling in tests. */
    const val HEADER_ITEMS = 3

    fun sheet(id: SheetId) = "library:sheet:${id.value}"
}

/**
 * The launcher menu: start a new sheet, continue the last one, or pick an older one from the
 * grid. Long-press a sheet for rename, duplicate and delete; delete asks first and then offers
 * undo for ten seconds. Stateless: the caller supplies the data and performs the actions.
 */
@Composable
fun LibraryScreen(
    sheets: List<SheetSummary>,
    lastOpened: SheetSummary?,
    onNewSheet: () -> Unit,
    onOpenSheet: (SheetId) -> Unit,
    onRename: (SheetId, String) -> Unit,
    onDuplicate: (SheetId) -> Unit,
    onDelete: (SheetId) -> Unit,
    onRestore: (SheetId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<SheetSummary?>(null) }
    var deleting by remember { mutableStateOf<SheetSummary?>(null) }
    val deletedMessage = stringResource(R.string.library_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(
        modifier = modifier.fillMaxSize().testTag(LibraryTags.SCREEN),
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                androidx.compose.material3.Snackbar(
                    action = {
                        TextButton(onClick = { data.performAction() }, modifier = Modifier.testTag(LibraryTags.UNDO)) {
                            Text(data.visuals.actionLabel.orEmpty())
                        }
                    },
                ) { Text(data.visuals.message) }
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(LibraryTags.GRID),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "new") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = onNewSheet, modifier = Modifier.fillMaxWidth().testTag(LibraryTags.NEW_SHEET)) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                        Text(stringResource(R.string.action_new_sheet), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                if (lastOpened != null) ContinueCard(lastOpened, onClick = { onOpenSheet(lastOpened.id) })
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                if (sheets.isNotEmpty()) {
                    Text(stringResource(R.string.library_all_sheets), style = MaterialTheme.typography.titleMedium)
                }
            }
            items(sheets, key = { it.id.value }) { sheet ->
                SheetCard(
                    sheet = sheet,
                    onOpen = { onOpenSheet(sheet.id) },
                    onRename = { renaming = sheet },
                    onDuplicate = { onDuplicate(sheet.id) },
                    onDelete = { deleting = sheet },
                )
            }
        }
    }

    renaming?.let { sheet ->
        RenameDialog(
            initial = sheet.title,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                renaming = null
                onRename(sheet.id, title)
            },
        )
    }
    deleting?.let { sheet ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_message, sheet.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        onDelete(sheet.id)
                        scope.launch {
                            val result = snackbarHost.showSnackbar(
                                message = deletedMessage.format(sheet.title),
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) onRestore(sheet.id)
                        }
                    },
                    modifier = Modifier.testTag(LibraryTags.DIALOG_CONFIRM),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ContinueCard(sheet: SheetSummary, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(LibraryTags.CONTINUE)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(sheet.thumbnail, Modifier.size(width = 96.dp, height = 72.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(stringResource(R.string.action_continue), style = MaterialTheme.typography.labelLarge)
                Text(sheet.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetCard(
    sheet: SheetSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LibraryTags.sheet(sheet.id))
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
        ) {
            Thumbnail(sheet.thumbnail, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            Column(Modifier.padding(12.dp)) {
                Text(sheet.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    DateUtils.getRelativeTimeSpanString(sheet.updatedAt).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = { menuOpen = false; onRename() },
                modifier = Modifier.testTag(LibraryTags.MENU_RENAME),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_duplicate)) },
                onClick = { menuOpen = false; onDuplicate() },
                modifier = Modifier.testTag(LibraryTags.MENU_DUPLICATE),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = { menuOpen = false; onDelete() },
                modifier = Modifier.testTag(LibraryTags.MENU_DELETE),
            )
        }
    }
}

/** The sheet's rendered preview on a neutral surface; Coil keys files by path and modification time. */
@Composable
private fun Thumbnail(file: File?, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White),
            )
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val normalized = SheetTitles.normalize(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = normalized == null,
                supportingText = {
                    if (normalized == null) {
                        Text(pluralStringResource(R.plurals.library_title_invalid, SheetTitles.MAX_LENGTH, SheetTitles.MAX_LENGTH))
                    }
                },
                modifier = Modifier.testTag(LibraryTags.RENAME_FIELD),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { normalized?.let(onConfirm) },
                enabled = normalized != null,
                modifier = Modifier.testTag(LibraryTags.DIALOG_CONFIRM),
            ) { Text(stringResource(R.string.action_rename)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@PreviewLightDark
@Composable
private fun LibraryScreenPreview() {
    val sheets = listOf("Garden ideas", "Sheet 2", "Sheet 1").mapIndexed { i, title ->
        SheetSummary(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9" + "VWX"[i]), title, File(""), 0L, 0L, null, null, 0)
    }
    io.github.subhaneetshrestha.afnopadd.shell.theme.AfnopaddTheme {
        LibraryScreen(sheets, sheets.first(), {}, {}, { _, _ -> }, {}, {}, {})
    }
}
