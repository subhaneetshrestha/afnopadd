package io.github.subhaneetshrestha.afnopadd.shell

import android.content.Context
import java.io.File

/** Root of all sheet directories (`files/sheets/<sheet id>/`). */
fun sheetsRoot(context: Context): File = File(context.filesDir, "sheets")

/** Deleted sheets wait here for undo and the seven-day purge. */
fun trashRoot(context: Context): File = File(context.filesDir, "trash")
