package interpret

import kotlin.reflect.KFunction

public fun interpreterOf(function: KFunction<*>): Interpreter {
    TODO()
}

internal fun pluginApi(): Nothing {
    error("This function is implemented as a part of plugin api and has no implementation")
}
