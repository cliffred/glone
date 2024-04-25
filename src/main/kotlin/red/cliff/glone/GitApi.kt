package red.cliff.glone

import java.io.BufferedReader
import java.io.File
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class GitApi(maxConcurrentGitOperations: Int = 10) {

    private val gitOperationsSemaphore: Semaphore = Semaphore(maxConcurrentGitOperations)

    suspend fun cloneProject(workDir: File, project: Project): Result<Unit> {
        val repoDir = project.getRepoDir(workDir)
        return gitCommand(
            workDir,
            "clone",
            "--filter=blob:none",
            project.sshUrlToRepo,
            repoDir.absolutePath,
        )
    }

    suspend fun pullProject(workDir: File, project: Project): Result<Unit> {
        val repoDir = project.getRepoDir(workDir)

        gitCommand(repoDir, "fetch").onFailure { return Result.failure(it) }
        return gitCommand(repoDir, "rebase").recover {
            gitCommand(repoDir, "rebase", "--abort")
        }
    }

    private fun Project.getRepoDir(workDir: File) = workDir.resolve(pathWithNamespace)

    private suspend fun gitCommand(workDir: File, vararg args: String): Result<Unit> {
        gitOperationsSemaphore.withPermit {
            val process =
                ProcessBuilder()
                    .directory(workDir)
                    .command("git", *args)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()

            val stdErr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()

            return if (exitCode == 0) Result.success(Unit) else Result.failure(GitException(stdErr))
        }
    }

    private class GitException(message: String) : RuntimeException(message)
}
