package red.cliff.glone

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

const val DEFAULT_MAX_CONCURRENT_GITLAB_CALLS = 10
const val DEFAULT_MAX_CONCURRENT_GIT_OPERATIONS = 50

class GloneCommand(
    private val runner: suspend (
        groups: List<String>,
        projects: List<String>,
        maxConcurrentCalls: Int,
        maxConcurrentOps: Int,
    ) -> Unit = ::defaultRunner,
) : CoreSuspendingCliktCommand(name = "glone") {

    init {
        context {
            exitProcess = { kotlin.system.exitProcess(it) }
            echoMessage = { _, message, trailingNewline, error ->
                val text = if (trailingNewline) "$message\n" else "$message"
                if (error) System.err.print(text) else print(text)
            }
        }
    }

    val groups by option("-g", "--group", help = "GitLab group to sync").multiple()
    val projects by option("-p", "--project", help = "GitLab project to sync").multiple()

    val maxConcurrentGitlabCalls by option(
        "--max-concurrent-gitlab-calls",
        help = "Maximum concurrent GitLab API calls (default: $DEFAULT_MAX_CONCURRENT_GITLAB_CALLS)",
    ).int().default(DEFAULT_MAX_CONCURRENT_GITLAB_CALLS).check("Must be at least 1") { it >= 1 }

    val maxConcurrentGitOperations by option(
        "--max-concurrent-git-operations",
        help = "Maximum concurrent Git operations (default: $DEFAULT_MAX_CONCURRENT_GIT_OPERATIONS)",
    ).int().default(DEFAULT_MAX_CONCURRENT_GIT_OPERATIONS).check("Must be at least 1") { it >= 1 }

    override suspend fun run() {
        if (groups.isEmpty() && projects.isEmpty()) {
            throw UsageError("Must specify at least one group or project")
        }
        runner(groups, projects, maxConcurrentGitlabCalls, maxConcurrentGitOperations)
    }
}

suspend fun defaultRunner(
    groups: List<String>,
    projects: List<String>,
    maxConcurrentCalls: Int,
    maxConcurrentOps: Int,
) {
    val git = GitApi(maxConcurrentOps)
    val gitlabApi = GitlabApi(
        apiHost = System.getenv("GITLAB_HOST") ?: "gitlab.com",
        maxConcurrentCalls = maxConcurrentCalls,
    )
    val gloneApp = GloneApp(gitlabApi, git)
    gloneApp.glone(groups = groups, projects = projects)
}

suspend fun main(args: Array<String>) = GloneCommand().main(args)
