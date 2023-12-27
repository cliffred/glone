package red.cliff.glone

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SpinnerTest :
    ShouldSpec({
        should("show latest text after each delay").config(coroutineTestScope = true) {
            val output = CapturingOutputConsole()
            OutputContext.setOutputConsole(output)

            val spinner = Spinner(1.seconds)

            launch { spinner.start("starting") }
            repeat(10) {
                delay(1.seconds)
                spinner.setText("text$it")
            }
            delay(2.seconds)
            spinner.stop()
            output.toString() shouldBe
                """
                ⠋ starting
                ⠙ text0   
                ⠹ text1
                ⠸ text2
                ⠼ text3
                ⠴ text4
                ⠦ text5
                ⠧ text6
                ⠇ text7
                ⠏ text8
                ⠋ text9
                ⠙ text9
                       
                
                """
                    .trimIndent()
        }
    })
