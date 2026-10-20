package io.github.subhaneetshrestha.afnopadd

import android.os.Build

/**
 * Timing targets in SPEC.md are for the reference phone. Emulators share the host CPU with
 * everything else running, so instrumented timing checks get twice the budget there; the
 * authoritative device measurements are taken in Task 25.
 */
object PerfBudget {

    val isEmulator: Boolean =
        Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu") || Build.PRODUCT.startsWith("sdk_gphone")

    fun millis(onDevice: Long): Long = if (isEmulator) onDevice * 2 else onDevice

    fun millis(onDevice: Double): Double = if (isEmulator) onDevice * 2 else onDevice
}
