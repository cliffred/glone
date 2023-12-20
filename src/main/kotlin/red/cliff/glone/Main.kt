package red.cliff.glone

import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

suspend fun main(groups: Array<String>) = coroutineScope {
    val httpCallsSemaphore = Semaphore(10)
    val gitOperationsSemaphore = Semaphore(50)

    val git = GitApi(gitOperationsSemaphore)

    val workDir = File(System.getProperty("user.dir"))
    val existingGitDirs =
        workDir.walkTopDown().filter { it.isDirectory && it.resolve(".git").exists() }.toHashSet()
    val spinner = Spinner(50.milliseconds)
    val fetchedGitDirs: MutableSet<File> = mutableSetOf()
    val cloneResults = mutableListOf<ProjectResult<Unit>>()
    val pullResults = mutableListOf<ProjectResult<Unit>>()

    launch { spinner.start("fetching projects...") }

    GitlabApi(httpCallsSemaphore = httpCallsSemaphore).use { gitlab ->
        coroutineScope {
            groups.forEach { group ->
                launch {
                    val projects: Flow<Project> = gitlab.getProjects(group)
                    withContext(Dispatchers.IO) {
                        projects
                            .filterNot { it.archived || it.emptyRepo }
                            .collect { project ->
                                val repoDir = workDir.resolve(project.pathWithNamespace)
                                fetchedGitDirs += repoDir
                                launch {
                                    if (repoDir !in existingGitDirs) {
                                        val result = git.cloneProject(workDir, project)
                                        cloneResults += ProjectResult(project, result)
                                    } else {
                                        val result = git.pullProject(workDir, project)
                                        pullResults += ProjectResult(project, result)
                                    }
                                    spinner.setText(project.path)
                                }
                            }
                    }
                }
            }
        }

        spinner.stop()
        printResults(cloneResults, pullResults, existingGitDirs, fetchedGitDirs, workDir)
    }
}

private fun printResults(
    cloneResults: List<ProjectResult<Unit>>,
    pullResults: List<ProjectResult<Unit>>,
    existingGitDirs: HashSet<File>,
    fetchedGitDirs: Set<File>,
    workDir: File
) {
    val clonedProjects =
        cloneResults.filter { it.result.isSuccess }.map { it.project.pathWithNamespace }
    if (cloneResults.isNotEmpty()) {
        echo("Cloned the following projects:")
        clonedProjects.forEach { echo("  $it") }
    }
    echo("Pulled ${pullResults.filter { it.result.isSuccess }.size} projects")
    val removedRepos = (existingGitDirs - fetchedGitDirs).map { it.relativeTo(workDir) }
    if (removedRepos.isNotEmpty()) {
        echo(
            "${removedRepos.size} repositories don't exist anymore, you can remove them with the following command:"
        )
        echo("rm -rf ${removedRepos.joinToString(" \\\n")}")
        echo()
    }
    cloneResults.forEach { result ->
        result.result.onFailure {
            echo(
                "Error cloning ${result.project.pathWithNamespace}:\n  ${it.indentMessage()}",
                err = true
            )
        }
    }
    pullResults.forEach { result ->
        result.result.onFailure {
            echo(
                "Error pulling ${result.project.pathWithNamespace}:\n  ${it.indentMessage()}",
                err = true
            )
        }
    }
}

private fun Throwable.indentMessage(spaces: Int = 2) =
    message?.replace(lineSeparators, "\n${" ".repeat(spaces)}") ?: "null"
