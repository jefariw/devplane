package io.github.spollito.devplane.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import io.github.spollito.devplane.provisioning.GitHubClient.CreatedRepository;
import io.github.spollito.devplane.templates.NewProject;
import io.github.spollito.devplane.templates.ProjectGenerator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.FileMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pushes generated projects to local bare repositories and clones them back. */
class ProjectPusherTests {

	private static final NewProject ORDERS_API = new NewProject("orders-api", "payments-team",
			"Order management REST API");

	private static final URI HTML_URL = URI.create("https://github.com/octo-dev/orders-api");

	private static final String MESSAGE = "Create orders-api from the java-rest-api template";

	private final ProjectGenerator generator = new ProjectGenerator("io.github.spollito");

	private final ProjectPusher pusher = new ProjectPusher(
			new GitHubProperties(FakeGitHub.TOKEN, URI.create("https://api.github.com")));

	@TempDir
	private Path workspace;

	private Path project;

	private Path remote;

	@BeforeEach
	void generateProjectAndRemote() throws Exception {
		project = workspace.resolve("project");
		generator.generate("java-rest-api", ORDERS_API, project);
		remote = workspace.resolve("orders-api.git");
		Git.init().setBare(true).setInitialBranch("main").setDirectory(remote.toFile()).call().close();
	}

	@Test
	void pushesOneCommitAsTheTokenOwner() {
		pusher.push(project, repositoryAt(remote), MESSAGE);

		assertThat(cloneRemote().commits()).singleElement().satisfies(commit -> {
			assertThat(commit.getFullMessage()).isEqualTo(MESSAGE);
			assertThat(commit.getAuthorIdent().getName()).isEqualTo(FakeGitHub.OWNER_LOGIN);
			assertThat(commit.getAuthorIdent().getEmailAddress()).isEqualTo("7654321+octo-dev@users.noreply.github.com");
			assertThat(commit.getCommitterIdent()).isEqualTo(commit.getAuthorIdent());
		});
	}

	@Test
	void pushesExactlyTheGeneratedFiles() {
		Path reference = workspace.resolve("reference");
		generator.generate("java-rest-api", ORDERS_API, reference);

		pusher.push(project, repositoryAt(remote), MESSAGE);

		assertThat(cloneRemote().files()).containsKeys("gradlew", "gradlew.bat", "README.md")
			.isEqualTo(ClonedRepository.files(reference));
	}

	@Test
	void recordsTheGradleWrapperAsExecutable() {
		pusher.push(project, repositoryAt(remote), MESSAGE);

		ClonedRepository clone = cloneRemote();
		assertThat(clone.modes()).containsEntry("gradlew", FileMode.EXECUTABLE_FILE);
		assertThat(clone.modes()).allSatisfy((path, mode) -> {
			if (!path.equals("gradlew")) {
				assertThat(mode).as(path).isEqualTo(FileMode.REGULAR_FILE);
			}
		});
	}

	@Test
	void recordsTheGradleWrapperAsExecutableWhenTheFileIsNot() throws IOException {
		if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
			Files.setPosixFilePermissions(project.resolve("gradlew"), PosixFilePermissions.fromString("rw-r--r--"));
		}

		pusher.push(project, repositoryAt(remote), MESSAGE);

		assertThat(cloneRemote().modes()).containsEntry("gradlew", FileMode.EXECUTABLE_FILE);
	}

	@Test
	void keepsTheTokenOutOfTheRepositoryConfiguration() throws IOException {
		pusher.push(project, repositoryAt(remote), MESSAGE);

		assertThat(project.resolve(".git/config")).content().doesNotContain(FakeGitHub.TOKEN);
	}

	@Test
	void reportsARejectedPush() {
		Path other = workspace.resolve("other");
		generator.generate("java-rest-api", new NewProject("payments-api", "payments-team", null), other);
		pusher.push(other, repositoryAt(remote), "Someone else's first commit");

		assertThatExceptionOfType(GitHubException.class)
			.isThrownBy(() -> pusher.push(project, repositoryAt(remote), MESSAGE))
			.satisfies(ex -> assertThat(ex.getBody().getDetail()).contains(HTML_URL.toString()));
	}

	@Test
	void reportsARepositoryThatCannotBeReached() {
		assertThatExceptionOfType(GitHubException.class)
			.isThrownBy(() -> pusher.push(project, repositoryAt(workspace.resolve("missing.git")), MESSAGE))
			.satisfies(ex -> assertThat(ex.getBody().getDetail()).startsWith("GitHub created " + HTML_URL + ", but"));
	}

	private static CreatedRepository repositoryAt(Path remote) {
		return new CreatedRepository(HTML_URL, remote.toUri(),
				new CreatedRepository.Owner(FakeGitHub.OWNER_LOGIN, FakeGitHub.OWNER_ID));
	}

	private ClonedRepository cloneRemote() {
		return ClonedRepository.of(remote, workspace.resolve("clone"));
	}

}
