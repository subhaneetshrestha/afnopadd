package io.github.subhaneetshrestha.afnopadd

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Timing targets in docs/product/spec.md are for the reference phone. Emulators share the host CPU with
 * everything else running, so instrumented timing checks get twice the budget there; the
 * authoritative device measurements are taken in Task 25. CI's shared runners are slower and noisier
 * still, so a run can set the multiple with the `perfBudgetScale` instrumentation argument.
 */
object PerfBudget {

    val isEmulator: Boolean =
        Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu") || Build.PRODUCT.startsWith("sdk_gphone")

    /** How many times the on-device budget this run gets. */
    val scale: Double =
        InstrumentationRegistry.getArguments().getString("perfBudgetScale")?.toDoubleOrNull() ?: if (isEmulator) 2.0 else 1.0

    fun millis(onDevice: Long): Long = (onDevice * scale).toLong()

    fun millis(onDevice: Double): Double = onDevice * scale
}
