package red.cliff.glone

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
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
import java.net.URLEncoder

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
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    fun getProjects(group: String): Flow<Project> = channelFlow {
        val totalItems = getTotalItems(group)
        val totalPages = totalItems / pageSize + 1

        coroutineScope {
            (1..totalPages).forEach { page ->
                launch {
                    httpCallsSemaphore.withPermit {
                        client.fetchPage(group, pageSize, page).body<List<Project>>().forEach { send(it) }
                    }
                }
            }
        }
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

    private suspend fun HttpClient.fetchPage(
        groupId: String,
        pageSize: Int,
        page: Int,
    ): HttpResponse = get {
        queryGroupProjects(groupId, pageSize, page)
    }

    private suspend fun getTotalItems(groupId: String): Int = client.head {
        queryGroupProjects(groupId, 1, 1)
    }.headers["x-total"]!!.toInt()

    private fun HttpRequestBuilder.queryGroupProjects(
        groupId: String,
        pageSize: Int,
        page: Int,
    ) {
        url("https://gitlab.com/api/v4/groups/${URLEncoder.encode(groupId, "UTF-8")}/projects")
//        url("http://localhost:6666/api/v4/groups/${URLEncoder.encode(groupId, "UTF-8")}/projects")

        header("PRIVATE-TOKEN", token)
        parameter("per_page", "$pageSize")
        parameter("page", "$page")
        parameter("include_subgroups", "true")
        parameter("order_by", "name")
    }
}
