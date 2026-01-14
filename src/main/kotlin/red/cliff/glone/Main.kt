package red.cliff.glone

suspend fun main(groups: Array<String>) {
    val maxConcurrentGitlabCalls = 10
    val maxConcurrentGitOperations = 50

    val git = GitApi(maxConcurrentGitOperations)

    val gitlabApi = GitlabApi(
        apiHost = System.getenv("GITLAB_HOST") ?: "gitlab.com",
        maxConcurrentCalls = maxConcurrentGitlabCalls,
    )

    val gloneApp = GloneApp(gitlabApi, git)

    gloneApp.glone(groups)
}
