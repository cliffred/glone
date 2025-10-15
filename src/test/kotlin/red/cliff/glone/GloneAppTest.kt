package red.cliff.glone

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

@OptIn(ExperimentalStdlibApi::class)
class GloneAppTest :
    ShouldSpec(
        {
            should("clone and pull projects").config(coroutineTestScope = true) {
                val output = CapturingOutputConsole()
                OutputContext.setOutputConsole(output)

                val projects = listOf("SparkQuest", "SkyWave", "TechRise").map { project(it) }

                val gitlabApi: GitlabApi =
                    mockk(relaxUnitFun = true) {
                        coEvery { getProjects("foo/bar") } returns
                            flow {
                                projects.forEach {
                                    emit(it)
                                    delay(1.seconds)
                                }
                            }
                    }

                val git: GitApi = mockk(relaxed = true)
                val workDir = tempdir()
                workDir.resolve("${projects[1].pathWithNamespace}/.git").mkdirs()
                workDir.resolve("is/removed1/.git").mkdirs()
                workDir.resolve("is/removed2/.git").mkdirs()

                val spinner = Spinner(1.seconds)

                val gloneApp =
                    GloneApp(
                        gitlabApi,
                        git,
                        workDir,
                        spinner,
                        coroutineContext[CoroutineDispatcher.Key]!!
                    )

                gloneApp.glone(arrayOf("foo/bar"))

                coVerify(exactly = 1) { git.cloneProject(workDir, projects[0]) }
                coVerify(exactly = 0) { git.cloneProject(workDir, projects[1]) }
                coVerify(exactly = 1) { git.cloneProject(workDir, projects[2]) }

                coVerify(exactly = 0) { git.pullProject(workDir, projects[0]) }
                coVerify(exactly = 1) { git.pullProject(workDir, projects[1]) }
                coVerify(exactly = 0) { git.pullProject(workDir, projects[2]) }

                output.toString() shouldBe
                    """
                    ⠋ fetching projects...
                    ⠙ sparkquest          
                    ⠹ skywave   
                    ⠸ techrise
                              
                    Cloned the following projects:
                      foo/bar/sparkquest
                      foo/bar/techrise
                    Pulled 1 projects
                    2 repositories don't exist anymore, you can remove them with the following command:
                    rm -rf \
                      is/removed1 \
                      is/removed2
                    

                """
                        .trimIndent()
            }
        },
    )
