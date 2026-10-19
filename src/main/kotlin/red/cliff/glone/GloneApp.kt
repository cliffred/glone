package red.cliff.glone

import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.readlnOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GloneApp(
    private val gitlabApi: GitlabApi,
    private val git: GitApi,
    private val workDir: File = File(System.getProperty("user.dir")),
    private val spinner: Spinner = Spinner(50.milliseconds),
    private val gitDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun glone(
        groups: List<String> = emptyList(),
        projects: List<String> = emptyList(),
    ) = coroutineScope {
        launch { spinner.start("fetching projects...") }

        val existingGitDirs: Set<File> =
            if (groups.isEmpty()) {
                emptySet()
            } else {
                workDir
                    .walkTopDown()
                    .filter { it.isDirectory && it.resolve(".git").exists() }
                    .filter { dir ->
                        groups.any { group -> isSubpathOfGroup(dir, group, workDir) }
                    }
                    .toSet()
            }

        val seenGitDirs = ConcurrentHashMap.newKeySet<File>()
        val fetchedGitDirs = ConcurrentHashMap.newKeySet<File>()
        val cloneResults = Collections.synchronizedList(mutableListOf<ProjectResult<Unit>>())
        val pullResults = Collections.synchronizedList(mutableListOf<ProjectResult<Unit>>())
        val notices = Collections.synchronizedList(mutableListOf<String>())

        gitlabApi.use { gitlab ->
            coroutineScope {
                groups.forEach { group ->
                    launch {
                        val groupProjects: Flow<Project> = gitlab.getProjects(group)
                        withContext(gitDispatcher) {
                            groupProjects
                                .filterNot { it.archived }
                                .collect { project ->
                                    this@withContext.syncProject(
                                        project,
                                        workDir,
                                        seenGitDirs,
                                        fetchedGitDirs,
                                        cloneResults,
                                        pullResults,
                                    )
                                }
                        }
                    }
                }

                projects.forEach { projectPath ->
                    launch {
                        val project = gitlab.getProject(projectPath)
                        if (project == null) {
                            notices += "Project $projectPath not found or forbidden"
                        } else if (project.archived) {
                            notices += "Project ${project.pathWithNamespace} is archived"
                        } else {
                            withContext(gitDispatcher) {
                                this@withContext.syncProject(
                                    project,
                                    workDir,
                                    seenGitDirs,
                                    fetchedGitDirs,
                                    cloneResults,
                                    pullResults,
                                )
                            }
                        }
                    }
                }
            }

            spinner.stop()
            printResults(cloneResults, pullResults, existingGitDirs, fetchedGitDirs, workDir, notices)
        }
    }

    private fun CoroutineScope.syncProject(
        project: Project,
        workDir: File,
        seenGitDirs: MutableSet<File>,
        fetchedGitDirs: MutableSet<File>,
        cloneResults: MutableList<ProjectResult<Unit>>,
        pullResults: MutableList<ProjectResult<Unit>>,
    ) {
        val repoDir = workDir.resolve(project.pathWithNamespace)
        if (seenGitDirs.add(repoDir.canonicalFile)) {
            fetchedGitDirs += repoDir
            launch {
                if (!repoDir.resolve(".git").exists()) {
                    val result = git.cloneProject(workDir, project)
                    cloneResults += ProjectResult(project, result)
                } else if (!project.emptyRepo) {
                    val result = git.pullProject(workDir, project)
                    pullResults += ProjectResult(project, result)
                }
                spinner.setText(project.path)
            }
        }
    }

    private fun printResults(
        cloneResults: List<ProjectResult<Unit>>,
        pullResults: List<ProjectResult<Unit>>,
        existingGitDirs: Set<File>,
        fetchedGitDirs: Set<File>,
        workDir: File,
        notices: List<String>,
    ) {
        val clonedProjects =
            cloneResults.filter { it.result.isSuccess }.map { it.project.pathWithNamespace }
        if (cloneResults.isNotEmpty()) {
            echo("Cloned the following projects:")
            clonedProjects.forEach { echo("  $it") }
        }
        echo("Pulled ${pullResults.filter { it.result.isSuccess }.size} projects")
        val fetchedCanonicalPaths = fetchedGitDirs.map { it.canonicalPath.lowercase() }.toSet()
        val removedRepos =
            existingGitDirs
                .filter { existing ->
                    existing.canonicalPath.lowercase() !in fetchedCanonicalPaths
                }
                .sortedBy { it.path }
        if (removedRepos.isNotEmpty()) {
            echo()
            echo("${removedRepos.size} repositories don't exist anymore on GitLab.")
            removedRepos.forEach { repo ->
                echo(
                    "Delete local repository ${repo.relativeTo(workDir)}? (y/N) ",
                    trailingNewline = false,
                )
                val answer = readlnOrNull()
                if (answer.equals("y", ignoreCase = true)) {
                    if (repo.deleteRecursively()) {
                        echo(" > Deleted.")
                    } else {
                        echo(" > Failed to delete.", error = true)
                    }
                }
            }
            echo()
        }
        notices.forEach { echo(it) }
        cloneResults.forEach { result ->
            result.result.onFailure {
                echo(
                    "Error cloning ${result.project.pathWithNamespace}:\n  ${it.indentMessage()}",
                    error = true,
                )
            }
        }
        pullResults.forEach { result ->
            result.result.onFailure {
                echo(
                    "Error pulling ${result.project.pathWithNamespace}:\n  ${it.indentMessage()}",
                    error = true,
                )
            }
        }
    }

    private fun isSubpathOfGroup(dir: File, group: String, workDir: File): Boolean {
        val workDirPath = workDir.canonicalFile.toPath()
        val dirPath = dir.canonicalFile.toPath()
        if (!dirPath.startsWith(workDirPath)) return false
        val relDir = workDirPath.relativize(dirPath)
        val groupPath = java.nio.file.Path.of(group).normalize()
        if (relDir.nameCount < groupPath.nameCount) return false
        for (i in 0 until groupPath.nameCount) {
            if (!relDir.getName(i).toString().equals(groupPath.getName(i).toString(), ignoreCase = true)) {
                return false
            }
        }
        return true
    }

    private fun Throwable.indentMessage(spaces: Int = 2) =
        message?.replace(lineSeparators, "\n${" ".repeat(spaces)}") ?: "null"
}
