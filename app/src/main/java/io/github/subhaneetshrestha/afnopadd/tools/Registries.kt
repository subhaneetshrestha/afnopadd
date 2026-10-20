package io.github.subhaneetshrestha.afnopadd.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.StateFlow

/** Tools by stable id, in registration order (the order a layout without explicit items would use). */
class ToolRegistry {

    private val tools = LinkedHashMap<String, Tool>()

    fun register(tool: Tool) {
        require(tool.id !in tools) { "Tool '${tool.id}' is already registered" }
        tools[tool.id] = tool
    }

    fun get(id: String): Tool? = tools[id]

    val all: List<Tool> get() = tools.values.toList()
}

/** An action with a stable id (`cmd:undo`) that toolbars, shortcuts and later plugins invoke. */
class Command(
    val id: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val enabled: StateFlow<Boolean>,
    val run: () -> Unit,
)

class CommandRegistry {

    private val commands = LinkedHashMap<String, Command>()

    fun register(command: Command) {
        require(command.id !in commands) { "Command '${command.id}' is already registered" }
        commands[command.id] = command
    }

    fun get(id: String): Command? = commands[id]

    val all: List<Command> get() = commands.values.toList()

    /** Runs the command if it exists and is enabled; returns whether it ran. */
    fun invoke(id: String): Boolean {
        val command = commands[id] ?: return false
        if (!command.enabled.value) return false
        command.run()
        return true
    }
}
