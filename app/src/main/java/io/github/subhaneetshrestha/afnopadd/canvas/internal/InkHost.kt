package io.github.subhaneetshrestha.afnopadd.canvas.internal

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.ink.authoring.InProgressStrokesView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * One [InProgressStrokesView] per activity, attached on top of the activity's content for the
 * activity's whole life and lent to whichever canvas that activity shows (ADR 0003).
 *
 * Jetpack Ink 1.0.0 crashes its render thread when its view is detached within about half a second
 * of drawing (`Unbalanced saves and restores`, or a SIGSEGV in libhwui), and starts a `HandlerThread`
 * per view that it never quits. A view that is never detached and never replaced avoids both: a
 * canvas leaving only takes its strokes off the view. The view covers the whole window, is
 * transparent when empty and ignores touches, so screens without a canvas are unaffected; canvases
 * offset their strokes by their position relative to it. Used from the UI thread only.
 */
internal object InkHost {

    private class Slot(val view: InProgressStrokesView, var inUse: Boolean)

    private val slots = HashMap<ComponentActivity, Slot>()

    /**
     * The view of the activity [context] belongs to, attached if it is not yet; null without an
     * activity or while another canvas holds it, and the caller then hosts a [create]d view itself.
     */
    fun acquire(context: Context): InProgressStrokesView? {
        val activity = context.findActivity() ?: return null
        // A destroyed activity never sends ON_DESTROY again, so its slot would be held forever.
        if (activity.lifecycle.currentState == Lifecycle.State.DESTROYED) return null
        val slot = slots.getOrPut(activity) {
            activity.lifecycle.addObserver(
                LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) slots.remove(activity) },
            )
            Slot(create(activity), inUse = false)
        }
        if (slot.inUse) return null
        if (slot.view.parent == null) {
            activity.addContentView(slot.view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        slot.inUse = true
        return slot.view
    }

    /** Clears everything the last canvas left on [released] and, if it is a hosted view, lends it out again. It stays attached. */
    fun release(released: InProgressStrokesView) {
        released.cancelUnfinishedStrokes()
        released.removeFinishedStrokes(released.getFinishedStrokes().keys)
        released.clearFinishedStrokesListeners()
        slots.values.firstOrNull { it.view === released }?.inUse = false
    }

    fun create(context: Context) = InProgressStrokesView(context).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        eagerInit()
    }

    private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
