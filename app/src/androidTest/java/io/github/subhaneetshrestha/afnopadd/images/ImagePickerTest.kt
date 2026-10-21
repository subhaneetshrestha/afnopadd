package io.github.subhaneetshrestha.afnopadd.images

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The picker hands each system picker the right request and passes back what it returns; nothing needs a permission. */
@RunWith(AndroidJUnit4::class)
class ImagePickerTest {

    @get:Rule
    val rule = createComposeRule()

    /** Answers every launch at once with [answer], recording what was asked for. */
    private class FakeRegistry(private val answer: (ActivityResultContract<*, *>, Any?) -> Any?) : ActivityResultRegistry() {
        val launched = mutableListOf<Pair<ActivityResultContract<*, *>, Any?>>()

        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += contract to input
            @Suppress("UNCHECKED_CAST")
            dispatchResult(requestCode, answer(contract, input) as O)
        }
    }

    private val picked = mutableListOf<List<Uri>>()
    private lateinit var launcher: ImagePickerLauncher

    private fun show(registry: FakeRegistry) {
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                launcher = rememberImagePicker { picked += it }
            }
        }
        rule.waitForIdle()
    }

    private val photo = Uri.parse("content://media/picker/0/1")

    @Test
    fun thePhotoPickerAsksForImagesAndHandsBackTheChoice() {
        val registry = FakeRegistry { _, _ -> listOf(photo) }
        show(registry)

        rule.runOnIdle { launcher.launch(ImageSource.PhotoPicker) }

        assertTrue(registry.launched.single().first is ActivityResultContracts.PickMultipleVisualMedia)
        assertEquals(listOf(listOf(photo)), picked)
    }

    @Test
    fun documentsAreFilteredToImagesAndBackingOutPicksNothing() {
        val registry = FakeRegistry { _, _ -> emptyList<Uri>() }
        show(registry)

        rule.runOnIdle { launcher.launch(ImageSource.Documents) }

        assertTrue((registry.launched.single().second as Array<*>).contentEquals(arrayOf("image/*")))
        assertTrue(picked.isEmpty())
    }

    @Test
    fun theCameraWritesIntoOurFileProviderAndOnlyASavedPhotoIsPicked() {
        val registry = FakeRegistry { _, _ -> true }
        show(registry)

        rule.runOnIdle { launcher.launch(ImageSource.Camera) }

        val target = registry.launched.single().second as Uri
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(CameraFiles.authority(context), target.authority)
        assertEquals(listOf(listOf(target)), picked)
    }

    @Test
    fun aCancelledPhotoIsNotPicked() {
        show(FakeRegistry { _, _ -> false })

        rule.runOnIdle { launcher.launch(ImageSource.Camera) }

        assertTrue(picked.isEmpty())
    }

    /** SPEC-image-board success criterion 5, against the merged manifest the app actually ships. */
    @Test
    fun theAppAsksForNoImageCameraOrStoragePermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        val forbidden = requested.filter { permission ->
            listOf("READ_MEDIA", "EXTERNAL_STORAGE", "CAMERA", "ACCESS_MEDIA_LOCATION").any { it in permission }
        }

        assertTrue("requested: $forbidden", forbidden.isEmpty())
    }
}
