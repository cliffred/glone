package red.cliff.glone

val lineSeparator: String = System.lineSeparator()

val lineSeparators = Regex("\r?\n")

fun echo(
    message: Any? = "",
    trailingNewline: Boolean = true,
    error: Boolean = false,
    console: OutputConsole = OutputContext.outputConsole
) {
    var output = message?.toString()?.replace(lineSeparators, lineSeparator) ?: "null"
    output = if (trailingNewline) "$output$lineSeparator" else output

    console.print(output, error)
}

interface OutputConsole {
    fun print(text: String, error: Boolean)
}

class SystemOutputConsole : OutputConsole {
    override fun print(text: String, error: Boolean) {
        (if (error) System.err else System.out).print(text)
    }
}

object OutputContext {

    private val outputConsoles: ThreadLocal<OutputConsole> = ThreadLocal.withInitial { SystemOutputConsole() }
    val outputConsole: OutputConsole
        get() = outputConsoles.get()

    fun setOutputConsole(outputConsole: OutputConsole) {
        outputConsoles.set(outputConsole)
    }
}
