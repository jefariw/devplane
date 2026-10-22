package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import io.github.spollito.devplane.IntegrationTest;
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
 * Scenarios from the service-creation spec's "Download a service's project" and
 * "Choose a project's template" requirements. Services are registered through the
 * API, because the catalog's repository is private to its package.
 */
@IntegrationTest
class DownloadProjectTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private ProjectGenerator generator;

	@TempDir
	private Path workspace;

	@BeforeEach
	void registerOrdersApi() {
		mvc.delete().uri("/api/v1/services/orders-api").exchange();
		assertThat(mvc.post().uri("/api/v1/services").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "orders-api", "owner": "payments-team", "description": "Order management REST API"}
				""")).hasStatus(HttpStatus.CREATED);
	}

	// Download a service's project

	@Test
	void downloadsAGeneratedProject() throws IOException {
		String registered = mvc.get().uri("/api/v1/services/orders-api").exchange().getResponse().getContentAsString();

		MvcTestResult result = mvc.get().uri("/api/v1/services/orders-api/project").exchange();

		assertThat(result).hasStatusOk()
			.hasContentType(ProjectController.APPLICATION_ZIP_VALUE)
			.hasHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"orders-api.zip\"");
		assertThat(ProjectArchives.files(archive(result))).isEqualTo(javaRestApiFiles());
		assertThat(mvc.get().uri("/api/v1/services/orders-api")).bodyJson().isStrictlyEqualTo(registered);
	}

	@Test
	void keepsTheGradleWrapperExecutable() throws IOException {
		MvcTestResult result = mvc.get().uri("/api/v1/services/orders-api/project").exchange();

		assertThat(ProjectArchives.unixModes(archive(result), workspace)).containsEntry("orders-api/gradlew",
				"rwxr-xr-x");
	}

	@Test
	void usesTheServicesCurrentDetails() throws IOException {
		assertThat(mvc.put().uri("/api/v1/services/orders-api").contentType(MediaType.APPLICATION_JSON).content("""
				{"owner": "checkout-team", "description": "Order management REST API", "lifecycle": "experimental"}
				""")).hasStatusOk();

		MvcTestResult result = mvc.get().uri("/api/v1/services/orders-api/project").exchange();

		assertThat(ProjectArchives.content(archive(result), "orders-api/README.md")).contains("checkout-team")
			.doesNotContain("payments-team");
	}

	@Test
	void returns404ForAnUnknownService() {
		assertThat(mvc.get().uri("/api/v1/services/unknown-service/project")).hasStatus(HttpStatus.NOT_FOUND)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo(404);
		assertThat(mvc.get()
			.uri("/api/v1/services/unknown-service/project")
			.accept(ProjectController.APPLICATION_ZIP_VALUE)).as("a client that accepts only zip archives")
			.hasStatus(HttpStatus.NOT_FOUND)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo(404);
	}

	// Choose a project's template

	@Test
	void acceptsAListedTemplate() throws IOException {
		MvcTestResult result = mvc.get().uri("/api/v1/services/orders-api/project?template=java-rest-api").exchange();

		assertThat(result).hasStatusOk();
		assertThat(ProjectArchives.files(archive(result))).isEqualTo(javaRestApiFiles());
	}

	@Test
	void rejectsAnUnknownTemplate() {
		MvcTestResult result = mvc.get().uri("/api/v1/services/orders-api/project?template=cobol-batch").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("template");
	}

	@Test
	void rejectsATestOnlyTemplate() {
		assertThat(mvc.get().uri("/api/v1/services/orders-api/project?template=test-template"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void checksTheTemplateBeforeTheService() {
		assertThat(mvc.get().uri("/api/v1/services/unknown-service/project?template=cobol-batch"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	/** What DevPlane generates from java-rest-api for orders-api as registered, by path in the archive. */
	private Map<String, String> javaRestApiFiles() throws IOException {
		Path project = workspace.resolve("expected");
		generator.generate("java-rest-api",
				new NewProject("orders-api", "payments-team", "Order management REST API"), project);
		return ProjectArchives.files(project, "orders-api/");
	}

	private static byte[] archive(MvcTestResult result) {
		return result.getResponse().getContentAsByteArray();
	}

}
