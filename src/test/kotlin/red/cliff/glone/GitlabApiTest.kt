package red.cliff.glone

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.toList

class GitlabApiTest :
    ShouldSpec({
        should("get single project with encoded path") {
            var requestedPath = ""
            val mockEngine = MockEngine { request ->
                requestedPath = request.url.encodedPath
                if (request.url.encodedPath.endsWith("projects/foo%2Fbar%2Fbaz")) {
                    respond(
                        content = """{
                            "id": "42",
                            "name": "Baz",
                            "name_with_namespace": "Foo / Bar / Baz",
                            "path": "baz",
                            "path_with_namespace": "foo/bar/baz",
                            "default_branch": "main",
                            "http_url_to_repo": "https://gitlab.com/foo/bar/baz.git",
                            "ssh_url_to_repo": "git@gitlab.com:foo/bar/baz.git",
                            "web_url": "https://gitlab.com/foo/bar/baz",
                            "archived": false,
                            "empty_repo": false
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond("Not Found", HttpStatusCode.NotFound)
                }
            }

            val api = GitlabApi(engine = mockEngine)
            val project = api.getProject("foo/bar/baz")

            requestedPath shouldBe "/api/v4/projects/foo%2Fbar%2Fbaz"
            project.shouldNotBeNull()
            project.id shouldBe "42"
            project.name shouldBe "Baz"
            project.pathWithNamespace shouldBe "foo/bar/baz"
            project.archived shouldBe false
        }

        should("return null on 404 Not Found") {
            val mockEngine = MockEngine {
                respond("Not Found", HttpStatusCode.NotFound)
            }
            val api = GitlabApi(engine = mockEngine)
            val project = api.getProject("missing/project")
            project.shouldBeNull()
        }

        should("return null on 403 Forbidden") {
            val mockEngine = MockEngine {
                respond("Forbidden", HttpStatusCode.Forbidden)
            }
            val api = GitlabApi(engine = mockEngine)
            val project = api.getProject("forbidden/project")
            project.shouldBeNull()
        }

        should("throw exception on 401 Unauthorized") {
            val mockEngine = MockEngine {
                respond("Unauthorized", HttpStatusCode.Unauthorized)
            }
            val api = GitlabApi(engine = mockEngine)
            io.kotest.assertions.throwables.shouldThrow<ClientRequestException> {
                api.getProject("forbidden/project")
            }
        }

        should("throw exception on 500 Internal Server Error") {
            val mockEngine = MockEngine {
                respond("Server Error", HttpStatusCode.InternalServerError)
            }
            val api = GitlabApi(engine = mockEngine)
            io.kotest.assertions.throwables.shouldThrow<ServerResponseException> {
                api.getProject("forbidden/project")
            }
        }

        should("get projects via mock engine") {
            val mockEngine = MockEngine { request ->
                if (request.method.value == "HEAD") {
                    respond(
                        content = "",
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf("application/json"),
                            "x-total" to listOf("2"),
                        ),
                    )
                } else {
                    respond(
                        content = """[
                            {
                                "id": "1",
                                "name": "P1",
                                "name_with_namespace": "Foo / P1",
                                "path": "p1",
                                "path_with_namespace": "foo/p1",
                                "default_branch": "main",
                                "http_url_to_repo": "https://gitlab.com/foo/p1.git",
                                "ssh_url_to_repo": "git@gitlab.com:foo/p1.git",
                                "web_url": "https://gitlab.com/foo/p1",
                                "archived": false,
                                "empty_repo": false
                            },
                            {
                                "id": "2",
                                "name": "P2",
                                "name_with_namespace": "Foo / P2",
                                "path": "p2",
                                "path_with_namespace": "foo/p2",
                                "default_branch": "main",
                                "http_url_to_repo": "https://gitlab.com/foo/p2.git",
                                "ssh_url_to_repo": "git@gitlab.com:foo/p2.git",
                                "web_url": "https://gitlab.com/foo/p2",
                                "archived": false,
                                "empty_repo": false
                            }
                        ]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }

            val gitlabApi = GitlabApi(engine = mockEngine)
            val result = gitlabApi
                .getProjects("foo")
                .filterNot { it.archived || it.emptyRepo }
                .toList()

            result.shouldHaveSize(2)
            result[0].name shouldBe "P1"
            result[1].name shouldBe "P2"
        }
    })
