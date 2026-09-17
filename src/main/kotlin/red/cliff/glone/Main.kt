package red.cliff.glone

const val DEFAULT_MAX_CONCURRENT_GITLAB_CALLS = 10
const val DEFAULT_MAX_CONCURRENT_GIT_OPERATIONS = 50

suspend fun main(groups: Array<String>) {
    val maxConcurrentGitOperations = System.getenv("MAX_CONCURRENT_GIT_OPERATIONS")?.toIntOrNull()
        ?: DEFAULT_MAX_CONCURRENT_GIT_OPERATIONS

    val maxConcurrentGitlabCalls = System.getenv("MAX_CONCURRENT_GITLAB_CALLS")?.toIntOrNull()
        ?: DEFAULT_MAX_CONCURRENT_GITLAB_CALLS

    val git = GitApi(maxConcurrentGitOperations)

    val gitlabApi = GitlabApi(
        apiHost = System.getenv("GITLAB_HOST") ?: "gitlab.com",
        maxConcurrentCalls = maxConcurrentGitlabCalls,
    )

    val gloneApp = GloneApp(gitlabApi, git)

    gloneApp.glone(groups)
}
