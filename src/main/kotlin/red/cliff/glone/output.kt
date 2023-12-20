package red.cliff.glone

val lineSeparator: String = System.lineSeparator()

val lineSeparators = Regex("\r?\n")

fun echo(message: Any? = "", trailingNewline: Boolean = true, err: Boolean = false) {
    var output = message?.toString()?.replace(lineSeparators, lineSeparator) ?: "null"
    output = if (trailingNewline) "$output$lineSeparator" else output

    (if (err) System.err else System.out).print(output)
}
