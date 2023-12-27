package red.cliff.glone

import kotlin.random.Random

fun project(
    name: String,
    id: String = Random.nextInt(1000, 9999).toString(),
    nameWithNamespace: String = "FOO B.V. / BAR / $name",
    path: String = name.slugify(),
    pathWithNamespace: String = "foo/bar/$path",
    defaultBranch: String = "main",
    httpUrlToRepo: String = "https://gitlab.com/$pathWithNamespace.git",
    sshUrlToRepo: String = "git@gitlab.com:$pathWithNamespace.git",
    webUrl: String = "https://gitlab.com/$pathWithNamespace",
    archived: Boolean = false,
    emptyRepo: Boolean = false,
) =
    Project(
        id,
        name,
        nameWithNamespace,
        path,
        pathWithNamespace,
        defaultBranch,
        httpUrlToRepo,
        sshUrlToRepo,
        webUrl,
        archived,
        emptyRepo,
    )

fun String.slugify(): String =
    this.lowercase()
        .replace(Regex("[^a-z0-9\\s]"), "") // Remove non-alphanumeric characters
        .replace(' ', '-') // Replace spaces with dashes
        .replace(Regex("-+"), "-") // Replace multiple dashes with a single dash

class CapturingOutputConsole : OutputConsole {
    private val outputBuffer = StringBuilder()

    override fun print(text: String, error: Boolean) {
        val msg =
            text.replace("\r", "") +
                if ('\r' in text && !text.endsWith(lineSeparator)) lineSeparator else ""

        outputBuffer.append(msg)
    }

    override fun toString() = outputBuffer.toString()
}
