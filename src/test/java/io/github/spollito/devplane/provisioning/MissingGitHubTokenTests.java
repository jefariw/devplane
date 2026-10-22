package io.github.spollito.devplane.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The "Provisioning needs a GitHub token" requirement. DevPlane runs here as it does
 * without {@code DEVPLANE_GITHUB_TOKEN}, so this test gets its own context instead
 * of the shared one with the fake GitHub. The empty property also overrides a token
 * set in the developer's environment.
 */
@SpringBootTest(properties = "devplane.github.token=")
@AutoConfigureMockMvc
class MissingGitHubTokenTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private GitHubProperties properties;

	@Test
	void reportsAMissingToken() {
		assertThat(mvc.post().uri("/api/v1/services").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "orders-api", "owner": "payments-team"}
				""")).hasStatus(HttpStatus.CREATED);

		assertThat(mvc.post().uri("/api/v1/services/orders-api/repository"))
			.hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("DEVPLANE_GITHUB_TOKEN");
		assertThat(mvc.get().uri("/api/v1/services/orders-api")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.repository")
			.isNull();
	}

	@Test
	void defaultsToGitHubsApi() {
		assertThat(properties.apiUrl()).isEqualTo(URI.create("https://api.github.com"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class DatabaseConfiguration {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgresContainer() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
		}

	}

}
