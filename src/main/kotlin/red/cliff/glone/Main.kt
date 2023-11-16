package red.cliff.glone

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

suspend fun main(projectNames: Array<String>) {
    GitlabApi().use { api ->
        coroutineScope {
            projectNames.forEach { name ->
                launch {
                    val projects: Flow<Project> = api.getProjects(name)
                    api.cloneProjects(projects)
                }
            }
        }
    }
}
