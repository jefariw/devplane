package io.github.spollito.devplane.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The "Database unreachable" scenario from the platform-api spec's "Health check"
 * requirement. It stops its database, so it gets a dedicated container and context
 * instead of the shared ones, and short connection timeouts so the check fails fast.
 */
@SpringBootTest(properties = { "spring.datasource.hikari.connection-timeout=1000",
		"spring.datasource.hikari.validation-timeout=500" })
@AutoConfigureMockMvc
@DirtiesContext
class HealthCheckDatabaseDownTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private PostgreSQLContainer postgres;

	@Test
	void databaseUnreachable() {
		postgres.stop();

		assertThat(mvc.get().uri("/actuator/health")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("DOWN");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class DedicatedDatabase {

		@Bean
		@ServiceConnection
		PostgreSQLContainer dedicatedPostgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
		}

	}

}
