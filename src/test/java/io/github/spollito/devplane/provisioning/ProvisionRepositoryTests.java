package io.github.spollito.devplane.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import io.github.spollito.devplane.IntegrationTest;
import io.github.spollito.devplane.MutableClock;
import io.github.spollito.devplane.provisioning.FakeGitHub.FakeRepository;
import io.github.spollito.devplane.templates.NewProject;
import io.github.spollito.devplane.templates.ProjectGenerator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.FileMode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Scenarios from the repository-provisioning spec, run against {@link FakeGitHub}.
 * Services are registered through the API, because the catalog's repository is
 * private to its package.
 */
@IntegrationTest
class ProvisionRepositoryTests {

	private static final Instant PROVISIONED_AT = Instant.parse("2026-03-02T14:05:30.250Z");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private FakeGitHub gitHub;

	@Autowired
	private MutableClock clock;

	@Autowired
	private ProjectGenerator generator;

	@TempDir
	private Path workspace;

	@BeforeEach
	void registerOrdersApi() {
		gitHub.reset();
		mvc.delete().uri("/api/v1/services/orders-api").exchange();
		assertThat(mvc.post().uri("/api/v1/services").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "orders-api", "owner": "payments-team", "description": "Order management REST API"}
				""")).hasStatus(HttpStatus.CREATED);
		clock.setInstant(PROVISIONED_AT);
	}

	// Provision a service's repository

	@Test
	void provisionsARepository() {
		MvcTestResult result = provision(null);

		assertThat(result).hasStatus(HttpStatus.CREATED)
			.hasHeader(HttpHeaders.LOCATION, "https://github.com/octo-dev/orders-api")
			.bodyJson()
			.isStrictlyEqualTo("""
					{
					  "url": "https://github.com/octo-dev/orders-api",
					  "template": "java-rest-api",
					  "createdAt": "2026-03-02T14:05:30.250Z"
					}
					""");
		assertThat(gitHub.repository("orders-api")).hasValueSatisfying(repository -> {
			assertThat(repository.htmlUrl()).hasToString("https://github.com/octo-dev/orders-api");
			assertThat(repository.description()).isEqualTo("Order management REST API");
		});
	}

	@Test
	void pushesTheGeneratedProjectAsOneCommit() {
		Path reference = workspace.resolve("reference");
		generator.generate("java-rest-api",
				new NewProject("orders-api", "payments-team", "Order management REST API"), reference);

		assertThat(provision(null)).hasStatus(HttpStatus.CREATED);

		ClonedRepository clone = cloneRepository("orders-api");
		assertThat(clone.commits()).singleElement().satisfies(commit -> {
			assertThat(commit.getFullMessage()).isEqualTo("Create orders-api from the java-rest-api template");
			assertThat(commit.getAuthorIdent().getEmailAddress()).isEqualTo("7654321+octo-dev@users.noreply.github.com");
		});
		assertThat(clone.files()).containsKey("README.md").isEqualTo(ClonedRepository.files(reference));
		assertThat(clone.modes()).containsEntry("gradlew", FileMode.EXECUTABLE_FILE);
	}

	@Test
	void recordsTheRepositoryOnTheService() throws Exception {
		String provisioned = provision(null).getResponse().getContentAsString();

		assertThat(mvc.get().uri("/api/v1/services/orders-api")).bodyJson().isLenientlyEqualTo("""
				{"repository": %s, "updatedAt": "2026-03-02T14:05:30.250Z"}
				""".formatted(provisioned));
	}

	@Test
	void returns404ForAnUnknownService() {
		assertThat(mvc.post().uri("/api/v1/services/unknown-service/repository")).hasStatus(HttpStatus.NOT_FOUND)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(gitHub.requests()).isEmpty();
	}

	@Test
	void leavesNoTemporaryDirectoryBehind() throws IOException {
		List<Path> before = temporaryDirectories();

		assertThat(provision(null)).hasStatus(HttpStatus.CREATED);

		assertThat(temporaryDirectories()).isEqualTo(before);
	}

	// Choose the template and visibility

	@Test
	void createsAPrivateRepositoryByDefault() {
		assertThat(provision("{}")).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.template")
			.isEqualTo("java-rest-api");
		assertThat(gitHub.repository("orders-api")).get().extracting(FakeRepository::isPrivate).isEqualTo(true);
	}

	@Test
	void createsAPublicRepositoryOnRequest() {
		assertThat(provision("""
				{"template": "java-rest-api", "visibility": "public"}
				""")).hasStatus(HttpStatus.CREATED);
		assertThat(gitHub.repository("orders-api")).get().extracting(FakeRepository::isPrivate).isEqualTo(false);
	}

	@Test
	void rejectsAnUnknownTemplateAndVisibility() {
		MvcTestResult result = provision("""
				{"template": "cobol-batch", "visibility": "internal"}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson()
			.extractingPath("$.errors[*].field")
			.asArray()
			.containsExactly("template", "visibility");
		assertThat(gitHub.requests()).isEmpty();
		assertThat(mvc.get().uri("/api/v1/services/orders-api")).bodyJson()
			.extractingPath("$.repository")
			.isNull();
	}

	// Provision a repository only once

	@Test
	void rejectsAServiceThatAlreadyHasARepository() throws Exception {
		assertThat(provision(null)).hasStatus(HttpStatus.CREATED);
		String service = mvc.get().uri("/api/v1/services/orders-api").exchange().getResponse().getContentAsString();
		gitHub.reset();

		assertThat(provision(null)).hasStatus(HttpStatus.CONFLICT)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Service 'orders-api' already has a repository: https://github.com/octo-dev/orders-api");
		assertThat(gitHub.requests()).isEmpty();
		assertThat(mvc.get().uri("/api/v1/services/orders-api")).bodyJson().isStrictlyEqualTo(service);
	}

	@Test
	void rejectsANameAlreadyTakenOnGitHub() throws IOException {
		FakeRepository existing = gitHub.addExistingRepository("orders-api");

		assertThat(provision(null)).hasStatus(HttpStatus.CONFLICT)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("The GitHub account already has a repository named 'orders-api'.");
		try (Git git = Git.open(existing.directory().toFile())) {
			assertThat(git.getRepository().exactRef("refs/heads/main")).as("pushed branch").isNull();
		}
		assertThat(mvc.get().uri("/api/v1/services/orders-api")).bodyJson()
			.extractingPath("$.repository")
			.isNull();
	}

	// Report GitHub failures

	@Test
	void reportsATokenThatGitHubRejects() {
		gitHub.respondWith(401, """
				{"message": "Bad credentials", "status": "401"}
				""");

		assertThat(provision(null)).hasStatus(HttpStatus.BAD_GATEWAY)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.startsWith("GitHub rejected the token.");
		assertNoRepositoryRecorded();
	}

	@Test
	void reportsAGitHubServerError() {
		gitHub.respondWith(500, """
				{"message": "Server Error"}
				""");

		assertThat(provision(null)).hasStatus(HttpStatus.BAD_GATEWAY)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertNoRepositoryRecorded();
	}

	@Test
	void reportsAFailedPushWithTheRepositoryLeftBehind() throws IOException {
		gitHub.breakPushes();
		List<Path> before = temporaryDirectories();

		assertThat(provision(null)).hasStatus(HttpStatus.BAD_GATEWAY)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("https://github.com/octo-dev/orders-api");
		assertThat(gitHub.repository("orders-api")).isPresent();
		assertNoRepositoryRecorded();
		assertThat(temporaryDirectories()).isEqualTo(before);
	}

	// Unregistering keeps the repository

	@Test
	void unregistersAServiceThatHasARepository() {
		assertThat(provision(null)).hasStatus(HttpStatus.CREATED);
		int requestsBefore = gitHub.requests().size();

		assertThat(mvc.delete().uri("/api/v1/services/orders-api")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(gitHub.requests()).hasSize(requestsBefore);
		assertThat(cloneRepository("orders-api").commits()).hasSize(1);
	}

	private MvcTestResult provision(@Nullable String body) {
		if (body == null) {
			return mvc.post().uri("/api/v1/services/orders-api/repository").exchange();
		}
		return mvc.post()
			.uri("/api/v1/services/orders-api/repository")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private void assertNoRepositoryRecorded() {
		assertThat(mvc.get().uri("/api/v1/services/orders-api")).bodyJson()
			.extractingPath("$.repository")
			.isNull();
	}

	private ClonedRepository cloneRepository(String name) {
		return ClonedRepository.of(gitHub.repository(name).orElseThrow().directory(), workspace.resolve("clone"));
	}

	private static List<Path> temporaryDirectories() throws IOException {
		try (Stream<Path> entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
			return entries.filter(path -> path.getFileName().toString().startsWith("devplane-")).sorted().toList();
		}
	}

}
