package io.github.subhaneetshrestha.afnopadd.shell

import android.content.Context
import java.io.File

/** Root of all sheet directories (`files/sheets/<id>/`); the library index arrives in Task 12. */
fun sheetsRoot(context: Context): File = File(context.filesDir, "sheets")

/** The single fixed sheet the editor opens until the library can choose one. */
fun placeholderSheetDir(context: Context): File = File(sheetsRoot(context), "placeholder")
