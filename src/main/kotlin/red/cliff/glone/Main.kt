package red.cliff.glone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File

suspend fun main(groups: Array<String>) {
    val httpCallsSemaphore = Semaphore(10)
    val gitOperationsSemaphore = Semaphore(50)

    val git = GitApi(gitOperationsSemaphore)

    val workDir = File(System.getProperty("user.dir"))
    val existingGitDirs = workDir
        .walkTopDown()
        .filter { it.isDirectory && it.resolve(".git").exists() }
        .toHashSet()
    val fetchedGitDirs: MutableSet<File> = mutableSetOf()

    GitlabApi(
        httpCallsSemaphore = httpCallsSemaphore,
    ).use { gitlab ->
        coroutineScope {
            groups.forEach { group ->
                launch {
                    val projects: Flow<Project> = gitlab.getProjects(group)
                    withContext(Dispatchers.IO) {
                        projects.filterNot { it.archived || it.emptyRepo }.collect { project ->
                            val repoDir = workDir.resolve(project.pathWithNamespace)
                            fetchedGitDirs += repoDir
                            launch {
                                if (repoDir !in existingGitDirs) {
                                    git.cloneProject(workDir, project)
                                } else {
                                    git.pullProject(workDir, project)
                                }
                            }
                        }
                    }
                }
            }
        }
        println("Done cloning repositories")
        val removedRepos = existingGitDirs - fetchedGitDirs
        if (removedRepos.isNotEmpty()) {
            println("The following repositories don't exist anymore:")
            removedRepos.forEach {
                println(it.relativeTo(workDir))
            }
        }
    }
}
