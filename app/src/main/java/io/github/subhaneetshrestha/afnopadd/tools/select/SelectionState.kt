package io.github.subhaneetshrestha.afnopadd.tools.select

import io.github.subhaneetshrestha.afnopadd.document.ElementId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The selected elements, shared by the select tool, the selection commands and the canvas overlay. */
class SelectionState {

    private val _selected = MutableStateFlow<Set<ElementId>>(emptySet())
    val selected: StateFlow<Set<ElementId>> = _selected.asStateFlow()

    private val _hasSelection = MutableStateFlow(false)
    /** True while anything is selected; the selection commands are enabled by it. */
    val hasSelection: StateFlow<Boolean> = _hasSelection.asStateFlow()

    fun set(ids: Set<ElementId>) {
        _selected.value = ids
        _hasSelection.value = ids.isNotEmpty()
    }

    /** Adds [id], or removes it if it is already selected (long-press). */
    fun toggle(id: ElementId) = set(if (id in _selected.value) _selected.value - id else _selected.value + id)

    fun clear() = set(emptySet())

    /** Drops ids that are no longer on the sheet, such as elements an undo removed. */
    fun retainOnly(live: Set<ElementId>) {
        val current = _selected.value
        if (current.any { it !in live }) set(current.filterTo(LinkedHashSet()) { it in live })
    }
}
