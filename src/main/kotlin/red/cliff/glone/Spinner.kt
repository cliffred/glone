package red.cliff.glone

import kotlin.time.Duration
import kotlinx.coroutines.delay

class Spinner(private val delay: Duration) {
    private val states = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    private var running = false
    private var latestText = ""
    private var maxLength = 0

    suspend fun start(initialText: String = "") {
        latestText = initialText
        running = true
        var i = 0
        while (running) {
            val state = states[i % states.size]
            clear()
            echo("\r$state $latestText", trailingNewline = false)
            delay(delay.inWholeMilliseconds)
            i++
        }
    }

    fun setText(text: String) {
        synchronized(this) { maxLength = maxOf(maxLength, text.length) }
        latestText = text
    }

    fun clear() {
        echo("\r${" ".repeat(maxLength + 2)}\r", trailingNewline = false)
    }

    fun stop() {
        running = false
        clear()
    }
}
