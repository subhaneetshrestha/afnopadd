package io.github.subhaneetshrestha.afnopadd.canvas.internal

import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.ink.authoring.InProgressStrokesView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * One [InProgressStrokesView] per activity, handed to whichever canvas that activity shows.
 *
 * Jetpack Ink 1.0.0's API 33+ render helper starts a `HandlerThread` for every view and never
 * quits it, so a view per editor visit leaked a thread, its looper and its GPU surfaces each time
 * (30 threads after 30 visits; on the emulator those GPU channels are backed by host memory).
 * A view that has been attached to one activity's window does not work in another's, so sharing
 * stops at the activity: its view is dropped when it is destroyed. Each activity recreation still
 * costs Ink one thread until the upstream bug is fixed. Used from the UI thread only.
 */
internal object SharedInkView {

    private class Slot(val view: InProgressStrokesView, var inUse: Boolean)

    private val slots = HashMap<ComponentActivity, Slot>()

    /** This activity's view, detached and cleared; a second canvas on screen at once gets a private view instead. */
    fun obtain(context: Context): InProgressStrokesView {
        val activity = context.findActivity() ?: return create(context)
        val slot = slots[activity]
        if (slot == null) {
            val view = create(activity)
            slots[activity] = Slot(view, inUse = true)
            activity.lifecycle.addObserver(
                LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) slots.remove(activity) },
            )
            return view
        }
        if (slot.inUse) return create(context)
        (slot.view.parent as? ViewGroup)?.removeView(slot.view)
        slot.inUse = true
        return slot.view
    }

    /** Clears everything the last canvas left in [released] and, if it is a shared view, makes it available again. */
    fun release(released: InProgressStrokesView) {
        released.cancelUnfinishedStrokes()
        released.removeFinishedStrokes(released.getFinishedStrokes().keys)
        released.clearFinishedStrokesListeners()
        slots.values.firstOrNull { it.view === released }?.inUse = false
    }

    private fun create(context: Context) = InProgressStrokesView(context).apply {
        isClickable = false
        isFocusable = false
        eagerInit()
    }

    private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
