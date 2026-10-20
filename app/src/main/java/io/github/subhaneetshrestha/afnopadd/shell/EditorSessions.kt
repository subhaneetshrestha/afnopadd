package io.github.subhaneetshrestha.afnopadd.shell

import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.document.Sheet

/**
 * The sheets the editor has open, by sheet id. An editor that leaves composition only because its
 * activity is being recreated (rotation, theme or window change) leaves its session here, and the
 * recreated editor takes it back: the same [Sheet], with its in-memory undo history, and the same
 * camera, instead of closing and reopening the database. Used from the UI thread only.
 */
class EditorSessions {

    class Session(val sheet: Sheet, val camera: CameraController)

    private val sessions = LinkedHashMap<String, Session>()

    /** Every open session, including ones kept across a configuration change. */
    val open: Map<String, Session> get() = sessions

    fun get(sheetId: String): Session? = sessions[sheetId]

    fun put(sheetId: String, session: Session) {
        sessions[sheetId] = session
    }

    fun remove(sheetId: String): Session? = sessions.remove(sheetId)
}
