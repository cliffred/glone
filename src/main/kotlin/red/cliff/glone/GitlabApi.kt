package red.cliff.glone

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import org.eclipse.jgit.util.FS
import java.io.Closeable
import java.io.File

class GitlabApi(
    private val baseDir: File = File(System.getProperty("user.dir")),
    private val token: String = System.getenv("GITLAB_TOKEN"),
    private val pageSize: Int = 10,
    private val httpCallsSemaphore: Semaphore = Semaphore(10),
    private val gitClonesSemaphore: Semaphore = Semaphore(10),
) : Closeable {

    init {
        setupSshSessionFactory()
    }

    private val client = HttpClient(CIO) {
        defaultRequest {
            url("https://gitlab.com/api/v4/")
            header("PRIVATE-TOKEN", token)
        }
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    fun getProjects(group: String): Flow<Project> = getAllPages {
        url("groups/${group.encodeURLPathPart()}/projects")
        parameter("include_subgroups", "true")
    }

    suspend fun cloneProjects(projects: Flow<Project>, dispatcher: CoroutineDispatcher = Dispatchers.IO) =
        withContext(dispatcher) {
            projects.filter { !it.archived }.collect { project ->
                val repoDir = baseDir.resolve(project.path_with_namespace)
                if (!repoDir.exists()) {
                    launch {
                        gitClonesSemaphore.withPermit {
                            Git.cloneRepository().setURI(project.ssh_url_to_repo).setDirectory(repoDir).call()
                            println("Cloned ${project.path_with_namespace}")
                        }
                    }
                }
            }
        }

    override fun close() {
        client.close()
    }

    /**
     * Sets up the SSH session factory.
     * Required for JGit to use the SSH keys from the ~/.ssh directory.
     */
    private fun setupSshSessionFactory() {
        val sshSessionFactory = SshdSessionFactoryBuilder()
            .setPreferredAuthentications("publickey")
            .setHomeDirectory(FS.DETECTED.userHome())
            .setSshDirectory(File(FS.DETECTED.userHome(), ".ssh"))
            .build(null)

        SshSessionFactory.setInstance(sshSessionFactory)
    }

    /**
     * Executes an [HttpClient]'s GET request for all pages and returns a [Flow] of elements
     * using the parameters configured in [block].
     */
    private inline fun <reified T> getAllPages(
        noinline block: (HttpRequestBuilder).() -> Unit,
    ): Flow<T> = channelFlow {
        coroutineScope {
            val totalItems = getTotalItems(block)
            val totalPages = totalItems / pageSize + 1

            (1..totalPages).map { page ->
                launch {
                    httpCallsSemaphore.withPermit {
                        getPage(pageSize, page, block).body<List<T>>().forEach { send(it) }
                    }
                }
            }
        }
    }

    private suspend fun getPage(
        pageSize: Int,
        page: Int,
        block: (HttpRequestBuilder).() -> Unit,
    ): HttpResponse = client.get {
        block()
        pageQuery(pageSize, page)
    }

    private suspend fun getTotalItems(block: (HttpRequestBuilder).() -> Unit): Int = client.head {
        block()
        pageQuery(1, 1)
    }.headers["x-total"]!!.toInt()

    private fun HttpRequestBuilder.pageQuery(
        pageSize: Int,
        page: Int,
    ) {
        parameter("per_page", "$pageSize")
        parameter("page", "$page")
    }
}
