package red.cliff.glone

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(SnakeCaseStrategy::class)
data class Project(
    val id: String,
    val name: String,
    val nameWithNamespace: String,
    val path: String,
    val pathWithNamespace: String,
    val defaultBranch: String,
    val httpUrlToRepo: String,
    val sshUrlToRepo: String,
    val webUrl: String,
    val archived: Boolean,
    val emptyRepo: Boolean,
)
