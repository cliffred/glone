package red.cliff.glone

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

class GitlabApiTest :
    ShouldSpec({
        should("get projects").config(enabledIf = { System.getenv("GITLAB_TOKEN") != null }) {
            val gitlabApi = GitlabApi()

            gitlabApi
                .getProjects("foo")
                .filterNot { it.archived || it.emptyRepo }
                .onEach { println(it) }
                .take(5)
                .toList()
                .shouldHaveSize(5)
        }
    })
