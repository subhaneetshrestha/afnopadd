package io.github.subhaneetshrestha.afnopadd.images

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Where images can come from (SPEC-image-board decision 1); none of them needs a permission. */
sealed interface ImageSource {
    data object PhotoPicker : ImageSource
    data object Documents : ImageSource
    data object Camera : ImageSource
}

/** Starts one of the system pickers; what the user picks arrives in the callback given to [rememberImagePicker]. */
class ImagePickerLauncher internal constructor(private val onLaunch: (ImageSource) -> Unit) {
    fun launch(source: ImageSource) = onLaunch(source)
}

/**
 * The system photo picker (up to [MAX_PICKED] images), the documents UI for image files, and the
 * system camera writing into a private cache file shared through a FileProvider. [onPicked] gets
 * the chosen URIs and is not called when the user backs out. The camera's target survives activity
 * recreation, since the camera app often runs while ours is destroyed.
 */
@Composable
fun rememberImagePicker(onPicked: (List<Uri>) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current
    val current by rememberUpdatedState(onPicked)
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED)) { uris ->
        if (uris.isNotEmpty()) current(uris)
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) current(uris.take(MAX_PICKED))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = cameraTarget?.let(Uri::parse)
        cameraTarget = null
        if (saved && target != null) current(listOf(target))
    }

    return remember(photos, documents, camera) {
        ImagePickerLauncher { source ->
            when (source) {
                ImageSource.PhotoPicker -> photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                ImageSource.Documents -> documents.launch(arrayOf("image/*"))
                ImageSource.Camera -> {
                    val target = CameraFiles.newTarget(context)
                    cameraTarget = target.toString()
                    camera.launch(target)
                }
            }
        }
    }
}

/** At most this many images per pick, the photo picker's own limit in the spec. */
const val MAX_PICKED = 20

/** Photos the system camera writes for us: private cache files, cleared once they are a day old. */
internal object CameraFiles {
    private const val DIR = "camera"

    fun newTarget(context: Context): Uri {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val dayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        dir.listFiles()?.filter { it.lastModified() < dayAgo }?.forEach { it.delete() }
        val file = File(dir, "photo-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    fun authority(context: Context) = "${context.packageName}.files"
}
