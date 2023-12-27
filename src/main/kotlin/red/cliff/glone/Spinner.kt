package red.cliff.glone

import kotlin.math.max
import kotlin.time.Duration
import kotlinx.coroutines.delay

class Spinner(private val delay: Duration) {
    private val states = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    private var running = false
    private var currentText = ""
    private var lastPrintedLength = 0

    suspend fun start(initialText: String = "") {
        setText(initialText)
        running = true
        var i = 0
        while (running) {
            val state = states[i % states.size]
            val message = "$state $currentText"
            val whiteSpace = " ".repeat(max(0, lastPrintedLength - message.length))
            echo("\r$message$whiteSpace", trailingNewline = false)
            lastPrintedLength = message.length
            delay(delay)
            if (++i >= states.size) i = 0
        }
    }

    fun setText(text: String) {
        currentText = text
    }

    private fun clear() {
        echo("\r${" ".repeat(lastPrintedLength)}\r", trailingNewline = false)
    }

    fun stop() {
        running = false
        clear()
    }
}
