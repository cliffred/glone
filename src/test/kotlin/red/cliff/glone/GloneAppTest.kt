package red.cliff.glone

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

class GloneAppTest :
    ShouldSpec(
        {
            should("clone and pull projects in groups and scope stale deletions").config(coroutineTestScope = true) {
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
                workDir.resolve("foo/bar/removed1/.git").mkdirs()
                workDir.resolve("foo/bar/removed2/.git").mkdirs()
                workDir.resolve("foo/barbaz/kept/.git").mkdirs()
                workDir.resolve("is/kept/.git").mkdirs()

                val spinner = Spinner(1.seconds)

                val gloneApp =
                    GloneApp(
                        gitlabApi,
                        git,
                        workDir,
                        spinner,
                        coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                    )

                gloneApp.glone(groups = listOf("foo/bar"))

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
                    
                    2 repositories don't exist anymore on GitLab.
                    Delete local repository foo/bar/removed1? (y/N) Delete local repository foo/bar/removed2? (y/N) 
                    
                """
                        .trimIndent()
            }

            should("clone projects in group concurrently").config(coroutineTestScope = true) {
                val projects = (1..5).map { project("Project$it") }
                val gitlabApi: GitlabApi =
                    mockk(relaxUnitFun = true) {
                        coEvery { getProjects("foo/bar") } returns
                            flow {
                                projects.forEach { emit(it) }
                            }
                    }

                val inFlight = AtomicInteger(0)
                val maxInFlight = AtomicInteger(0)
                val git: GitApi =
                    mockk(relaxed = true) {
                        coEvery { cloneProject(any(), any()) } coAnswers {
                            val current = inFlight.incrementAndGet()
                            maxInFlight.updateAndGet { maxOf(it, current) }
                            delay(100.milliseconds)
                            inFlight.decrementAndGet()
                            Result.success(Unit)
                        }
                    }

                val workDir = tempdir()
                val spinner = Spinner(1.seconds)
                val gloneApp =
                    GloneApp(
                        gitlabApi,
                        git,
                        workDir,
                        spinner,
                        coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                    )

                gloneApp.glone(groups = listOf("foo/bar"))

                maxInFlight.get() shouldBeGreaterThan 1
                coVerify(exactly = 5) { git.cloneProject(workDir, any()) }
            }

            should("sync individual projects without prompting deletion of existing repos").config(coroutineTestScope = true) {
                val output = CapturingOutputConsole()
                OutputContext.setOutputConsole(output)

                val proj = project("SoloProject")
                val gitlabApi: GitlabApi =
                    mockk(relaxUnitFun = true) {
                        coEvery { getProject("foo/bar/soloproject") } returns proj
                    }

                val git: GitApi = mockk(relaxed = true)
                val workDir = tempdir()
                workDir.resolve("unrelated/repo/.git").mkdirs()

                val spinner = Spinner(1.seconds)
                val gloneApp =
                    GloneApp(
                        gitlabApi,
                        git,
                        workDir,
                        spinner,
                        coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                    )

                gloneApp.glone(projects = listOf("foo/bar/soloproject"))

                coVerify(exactly = 1) { git.cloneProject(workDir, proj) }
                coVerify(exactly = 0) { git.pullProject(any(), any()) }
                output.toString() shouldContain "Cloned the following projects:\n  foo/bar/soloproject"
                output.toString() shouldContain "Pulled 0 projects"
                output.toString().contains("repositories don't exist anymore") shouldBe false
            }

            should("continue syncing remaining projects and groups when a project is not found or forbidden").config(coroutineTestScope = true) {
                val output = CapturingOutputConsole()
                OutputContext.setOutputConsole(output)

                val groupProj = project("GroupProj", pathWithNamespace = "foo/group/groupproj")
                val foundProj = project("FoundProj")
                val gitlabApi: GitlabApi =
                    mockk(relaxUnitFun = true) {
                        coEvery { getProjects("foo/group") } returns flow { emit(groupProj) }
                        coEvery { getProject("foo/bar/foundproj") } returns foundProj
                        coEvery { getProject("foo/bar/missing") } returns null
                    }

                val git: GitApi = mockk(relaxed = true)
                val workDir = tempdir()
                val spinner = Spinner(1.seconds)
                val gloneApp =
                    GloneApp(
                        gitlabApi,
                        git,
                        workDir,
                        spinner,
                        coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                    )

                gloneApp.glone(
                    groups = listOf("foo/group"),
                    projects = listOf("foo/bar/foundproj", "foo/bar/missing"),
                )

                coVerify(exactly = 1) { git.cloneProject(workDir, groupProj) }
                coVerify(exactly = 1) { git.cloneProject(workDir, foundProj) }
                output.toString() shouldContain "Cloned the following projects:\n  foo/group/groupproj\n  foo/bar/foundproj"
                output.toString() shouldContain "Project foo/bar/missing not found or forbidden"
            }

            should("deduplicate project present in both groups and projects on clone path").config(coroutineTestScope = true) {
                val proj = project("SharedProject")
                val gitlabApi: GitlabApi =
                    mockk(relaxUnitFun = true) {
                        coEvery { getProjects("foo/bar") } returns
                            flow {
                                emit(proj)
                            }
                        coEvery { getProject("foo/bar/sharedproject") } returns proj
                    }

                val git: GitApi = mockk(relaxed = true)
                val workDir = tempdir()

                val spinner = Spinner(1.seconds)
                val gloneApp =
                    GloneApp(
                        gitlabApi,
                        git,
                        workDir,
                        spinner,
                        coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                    )

                gloneApp.glone(
                    groups = listOf("foo/bar"),
                    projects = listOf("foo/bar/sharedproject"),
                )

                coVerify(exactly = 1) { git.cloneProject(workDir, proj) }
                coVerify(exactly = 0) { git.pullProject(any(), any()) }
            }

            should("deduplicate existing project present in both groups and projects on pull path").config(coroutineTestScope = true) {
                val proj = project("SharedProject")
                val gitlabApi: GitlabApi =
                    mockk(relaxUnitFun = true) {
                        coEvery { getProjects("foo/bar") } returns flow { emit(proj) }
                        coEvery { getProject("foo/bar/sharedproject") } returns proj
                    }

                val git: GitApi = mockk(relaxed = true)
                val workDir = tempdir()
                workDir.resolve("foo/bar/sharedproject/.git").mkdirs()

                val spinner = Spinner(1.seconds)
                val gloneApp =
                    GloneApp(
                        gitlabApi,
                        git,
                        workDir,
                        spinner,
                        coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                    )

                gloneApp.glone(
                    groups = listOf("foo/bar"),
                    projects = listOf("foo/bar/sharedproject"),
                )

                coVerify(exactly = 1) { git.pullProject(workDir, proj) }
                coVerify(exactly = 0) { git.cloneProject(any(), any()) }
            }

            should("handle mixed-case directories safely without false stale deletions").config(coroutineTestScope = true) {
                val output = CapturingOutputConsole()
                OutputContext.setOutputConsole(output)

                val proj = project("KeptProject")
                val gitlabApi: GitlabApi =
                    mockk(relaxUnitFun = true) {
                        coEvery { getProjects("Foo/Bar") } returns flow { emit(proj) }
                    }

                val git: GitApi = mockk(relaxed = true)
                val workDir = tempdir()
                workDir.resolve("foo/bar/KeptProject/.git").mkdirs()
                workDir.resolve("Foo/Bar/stale/.git").mkdirs()
                workDir.resolve("foo/barbaz/other/.git").mkdirs()

                val spinner = Spinner(1.seconds)
                val gloneApp =
                    GloneApp(
                        gitlabApi,
                        git,
                        workDir,
                        spinner,
                        coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                    )

                gloneApp.glone(groups = listOf("Foo/Bar"))

                coVerify(exactly = 1) { git.pullProject(workDir, proj) }
                coVerify(exactly = 0) { git.cloneProject(any(), any()) }

                val text = output.toString()
                text shouldContain "1 repositories don't exist anymore on GitLab."
                text shouldContain "Delete local repository"
                text shouldContain "stale? (y/N)"
                text.contains("KeptProject") shouldBe false
                text.contains("other") shouldBe false
            }

            should("record notice when project is not found or forbidden").config(coroutineTestScope = true) {
                val output = CapturingOutputConsole()
                OutputContext.setOutputConsole(output)

                val gitlabApi: GitlabApi =
                    mockk(relaxUnitFun = true) {
                        coEvery { getProject("missing/project") } returns null
                    }

                val git: GitApi = mockk(relaxed = true)
                val workDir = tempdir()

                val spinner = Spinner(1.seconds)
                val gloneApp =
                    GloneApp(
                        gitlabApi,
                        git,
                        workDir,
                        spinner,
                        coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                    )

                gloneApp.glone(projects = listOf("missing/project"))

                output.toString() shouldBe
                    """
                    ⠋ fetching projects...
                                          
                    Pulled 0 projects
                    Project missing/project not found or forbidden
                    
                """
                        .trimIndent()
            }

            should("record notice when explicit project is archived").config(coroutineTestScope = true) {
                val output = CapturingOutputConsole()
                OutputContext.setOutputConsole(output)

                val archivedProj = project("ArchivedProj", archived = true)
                val gitlabApi: GitlabApi =
                    mockk(relaxUnitFun = true) {
                        coEvery { getProject("foo/bar/archivedproj") } returns archivedProj
                    }

                val git: GitApi = mockk(relaxed = true)
                val workDir = tempdir()

                val spinner = Spinner(1.seconds)
                val gloneApp =
                    GloneApp(
                        gitlabApi,
                        git,
                        workDir,
                        spinner,
                        coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                    )

                gloneApp.glone(projects = listOf("foo/bar/archivedproj"))

                coVerify(exactly = 0) { git.cloneProject(any(), any()) }
                coVerify(exactly = 0) { git.pullProject(any(), any()) }
                output.toString() shouldBe
                    """
                    ⠋ fetching projects...
                                          
                    Pulled 0 projects
                    Project foo/bar/archivedproj is archived
                    
                """
                        .trimIndent()
            }
        },
    )
