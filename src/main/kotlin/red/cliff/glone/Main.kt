package red.cliff.glone

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

suspend fun main(groups: Array<String>) {
    val httpCallsSemaphore = Semaphore(10)
    val gitClonesSemaphore = Semaphore(100)

    GitlabApi(
        httpCallsSemaphore = httpCallsSemaphore,
        gitClonesSemaphore = gitClonesSemaphore,
    ).use { api ->
        coroutineScope {
            groups.forEach { group ->
                launch {
                    val projects: Flow<Project> = api.getProjects(group)
                    api.cloneProjects(projects)
                }
            }
        }
    }
}
