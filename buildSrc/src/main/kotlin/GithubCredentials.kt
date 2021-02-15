import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.credentials.PasswordCredentials

private data class UserDetails(val username: String, val password: String)

private fun githubUserDetails(project: Project): UserDetails? {
    val username: String? = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
    val password: String? = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")

    if (username == null || password == null) return null

    return UserDetails(username, password)
}

fun isGithubUserAvailable(project: Project) = githubUserDetails(project) != null

fun githubUserCredentials(project: Project): Action<PasswordCredentials> {
    val userDetails = githubUserDetails(project) ?: error("No Github user details available")
    return Action<PasswordCredentials> {
        username = userDetails.username
        password = userDetails.password
    }
}
