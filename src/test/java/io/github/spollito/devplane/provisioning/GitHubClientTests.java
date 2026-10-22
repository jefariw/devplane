package io.github.spollito.devplane.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.net.URI;

import io.github.spollito.devplane.provisioning.FakeGitHub.RecordedRequest;
import io.github.spollito.devplane.provisioning.GitHubClient.CreatedRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

/** Checks the GitHub REST API calls against {@link FakeGitHub}. */
@ExtendWith(OutputCaptureExtension.class)
class GitHubClientTests {

	private static FakeGitHub gitHub;

	private GitHubClient client;

	@BeforeAll
	static void startGitHub() {
		gitHub = FakeGitHub.start();
	}

	@AfterAll
	static void stopGitHub() throws IOException {
		gitHub.close();
	}

	@BeforeEach
	void resetGitHub() {
		gitHub.reset();
		client = new GitHubClient(new GitHubProperties(FakeGitHub.TOKEN, gitHub.url()));
	}

	@Test
	void createsAPrivateRepository() throws Exception {
		CreatedRepository created = client.createRepository("orders-api", "Order management REST API", true);

		assertThat(created.htmlUrl()).isEqualTo(URI.create("https://github.com/octo-dev/orders-api"));
		assertThat(created.cloneUrl()).isEqualTo(gitHub.repository("orders-api").orElseThrow().cloneUrl());
		assertThat(created.owner()).isEqualTo(new CreatedRepository.Owner(FakeGitHub.OWNER_LOGIN, FakeGitHub.OWNER_ID));
		assertThat(gitHub.repository("orders-api")).hasValueSatisfying(repository -> {
			assertThat(repository.isPrivate()).isTrue();
			assertThat(repository.description()).isEqualTo("Order management REST API");
		});
		RecordedRequest request = gitHub.requests().getFirst();
		assertThat(request.method()).isEqualTo("POST");
		assertThat(request.path()).isEqualTo("/user/repos");
		assertThat(request.headers()).containsEntry("authorization", "Bearer " + FakeGitHub.TOKEN)
			.containsEntry("accept", "application/vnd.github+json")
			.containsEntry("x-github-api-version", "2022-11-28")
			.containsEntry("content-type", "application/json");
		JSONAssert.assertEquals("""
				{"name": "orders-api", "description": "Order management REST API", "private": true, "auto_init": false}
				""", request.body(), JSONCompareMode.STRICT);
	}

	@Test
	void createsAPublicRepositoryWithoutADescription() throws Exception {
		client.createRepository("orders-api", null, false);

		assertThat(gitHub.repository("orders-api").orElseThrow().isPrivate()).isFalse();
		JSONAssert.assertEquals("""
				{"name": "orders-api", "private": false, "auto_init": false}
				""", gitHub.requests().getFirst().body(), JSONCompareMode.STRICT);
	}

	@Test
	void reportsANameThatIsAlreadyTaken() {
		gitHub.addExistingRepository("orders-api");

		assertThatExceptionOfType(RepositoryNameTakenException.class)
			.isThrownBy(() -> client.createRepository("orders-api", null, true))
			.satisfies(ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(ex.getBody().getDetail())
					.isEqualTo("The GitHub account already has a repository named 'orders-api'.");
			});
	}

	@Test
	void reportsOtherValidationFailuresAsGitHubFailures() {
		gitHub.respondWith(422, """
				{"message": "Validation Failed", "errors": [{"field": "description", "code": "invalid"}]}
				""");

		assertThatExceptionOfType(GitHubException.class)
			.isThrownBy(() -> client.createRepository("orders-api", null, true))
			.satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
	}

	@Test
	void reportsARejectedToken(CapturedOutput output) {
		GitHubClient client = new GitHubClient(new GitHubProperties("wrong-token-9d1e", gitHub.url()));

		assertThatExceptionOfType(GitHubException.class)
			.isThrownBy(() -> client.createRepository("orders-api", null, true))
			.satisfies(ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
				assertThat(ex.getBody().getDetail()).startsWith("GitHub rejected the token.");
			});
		assertThat(output).contains("GitHub answered 401", "request id FAKE:").doesNotContain("wrong-token-9d1e");
	}

	@Test
	void sendsATokenCopiedWithATrailingLineBreak() {
		GitHubClient client = new GitHubClient(new GitHubProperties(FakeGitHub.TOKEN + "\n", gitHub.url()));

		client.createRepository("orders-api", null, true);

		assertThat(gitHub.requests().getFirst().headers()).containsEntry("authorization", "Bearer " + FakeGitHub.TOKEN);
	}

	@Test
	void reportsATokenThatCannotBeSentWithoutLoggingIt(CapturedOutput output) {
		GitHubClient client = new GitHubClient(new GitHubProperties("github_pat_one\nline-two-31c7", gitHub.url()));

		assertThatExceptionOfType(GitHubException.class)
			.isThrownBy(() -> client.createRepository("orders-api", null, true))
			.satisfies(ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
				assertThat(ex.getBody().getDetail()).contains("DEVPLANE_GITHUB_TOKEN").doesNotContain("31c7");
				assertThat(ex.getCause()).isNull();
			});
		assertThat(gitHub.requests()).isEmpty();
		assertThat(output).contains("Could not send the GitHub token").doesNotContain("github_pat_one", "31c7");
	}

	@Test
	void reportsAServerErrorInItsOwnWords() {
		gitHub.respondWith(500, """
				{"message": "Internal details that must not leak"}
				""");

		assertThatExceptionOfType(GitHubException.class)
			.isThrownBy(() -> client.createRepository("orders-api", null, true))
			.satisfies(ex -> assertThat(ex.getBody().getDetail())
				.isEqualTo("GitHub could not create the repository (status 500)."));
	}

	@Test
	void reportsAnUnreachableGitHub(CapturedOutput output) throws IOException {
		FakeGitHub stopped = FakeGitHub.start();
		URI url = stopped.url();
		stopped.close();
		GitHubClient client = new GitHubClient(new GitHubProperties(FakeGitHub.TOKEN, url));

		assertThatExceptionOfType(GitHubException.class)
			.isThrownBy(() -> client.createRepository("orders-api", null, true))
			.satisfies(ex -> assertThat(ex.getBody().getDetail())
				.isEqualTo("Could not reach GitHub to create the repository."));
		assertThat(output).doesNotContain(FakeGitHub.TOKEN);
	}

}
