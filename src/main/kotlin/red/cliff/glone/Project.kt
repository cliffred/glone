package red.cliff.glone

data class Project(
    val id: String,
    val name: String,
    val name_with_namespace: String,
    val path: String,
    val path_with_namespace: String,
    val default_branch: String,
    val http_url_to_repo: String,
    val ssh_url_to_repo: String,
    val web_url: String,
    val archived: Boolean,
)
