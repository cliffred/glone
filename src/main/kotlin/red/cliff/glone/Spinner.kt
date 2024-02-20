package red.cliff.glone

import kotlin.math.max
import kotlin.time.Duration
import kotlinx.coroutines.delay

const val SAVE_CURSOR_POSITION = "\u001B[s"
const val RESTORE_CURSOR_POSITION = "\u001B[u"
const val CURSOR_TO_NEXT_LINE = "\u001B[B\r"

class Spinner(private val delay: Duration) {
    private val states = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    private var running = false
    private var currentText = ""
    private var lastPrintedLength = 0

    suspend fun start(initialText: String = "") {
        setText(initialText)
        running = true
        var i = 0
        echo(SAVE_CURSOR_POSITION, trailingNewline = false)
        while (running) {
            val state = states[i % states.size]
            val message = "$state $currentText"
            val whiteSpace = " ".repeat(max(0, lastPrintedLength - message.length))
            echo(RESTORE_CURSOR_POSITION, trailingNewline = false)
            echo("\r$message$whiteSpace", trailingNewline = false)
            echo(CURSOR_TO_NEXT_LINE, trailingNewline = false)
            lastPrintedLength = message.length
            delay(delay)
            if (++i >= states.size) i = 0
        }
    }

    fun setText(text: String) {
        currentText = text
    }

    private fun clear() {
        echo(RESTORE_CURSOR_POSITION, trailingNewline = false)
        echo("\r${" ".repeat(lastPrintedLength)}\r", trailingNewline = false)
    }

    fun stop() {
        running = false
        clear()
    }
}
