package red.cliff.glone

import java.io.BufferedReader
import java.io.File
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class GitApi(
    private val gitOperationsSemaphore: Semaphore = Semaphore(10),
) {

    suspend fun cloneProject(workDir: File, project: Project) {
        val repoDir = project.getRepoDir(workDir)
        gitCommand(
                workDir,
                "clone",
                "--filter=blob:none",
                project.sshUrlToRepo,
                repoDir.absolutePath
            )
            .onSuccess { println("Cloned ${project.pathWithNamespace}") }
            .onFailure {
                repoDir.deleteRecursively()
                System.err.println("Error cloning ${project.pathWithNamespace}:\n${it.message}")
            }
    }

    suspend fun pullProject(workDir: File, project: Project) {
        val repoDir = project.getRepoDir(workDir)
        gitCommand(repoDir, "pull", "--rebase")
            .onSuccess { println("Pulled ${project.pathWithNamespace}") }
            .onFailure {
                System.err.println("Error pulling ${project.pathWithNamespace}:\n${it.message}")
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
