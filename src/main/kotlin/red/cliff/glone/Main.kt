package red.cliff.glone

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

suspend fun main(groups: Array<String>) {
    val httpCallsSemaphore = Semaphore(10)
    val gitClonesSemaphore = Semaphore(10)

    GitlabApi(
        httpCallsSemaphore = httpCallsSemaphore,
        gitClonesSemaphore = gitClonesSemaphore,
    ).use { api ->
        coroutineScope {
            groups.forEach { name ->
                launch {
                    val projects: Flow<Project> = api.getProjects(name)
                    api.cloneProjects(projects)
                }
            }
        }
    }
}
