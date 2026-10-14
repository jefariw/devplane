package io.github.spollito.devplane.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.spollito.devplane.DevplaneApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The "Service is still listed after a restart" scenario from the service-catalog
 * spec. Runs the real application twice, on a real port, against one database.
 */
class CatalogSurvivesRestartTests {

	@Test
	void serviceIsStillListedAfterARestart() {
		try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
			postgres.start();
			String[] args = { "--spring.datasource.url=" + postgres.getJdbcUrl(),
					"--spring.datasource.username=" + postgres.getUsername(),
					"--spring.datasource.password=" + postgres.getPassword(), "--server.port=0" };

			ServiceResponse registered;
			try (ConfigurableApplicationContext app = SpringApplication.run(DevplaneApplication.class, args)) {
				registered = client(app).post()
					.uri("/api/v1/services")
					.contentType(MediaType.APPLICATION_JSON)
					.body("""
							{"name": "orders-api", "owner": "payments-team", "description": "Order management REST API"}
							""")
					.retrieve()
					.body(ServiceResponse.class);
			}

			try (ConfigurableApplicationContext app = SpringApplication.run(DevplaneApplication.class, args)) {
				ServiceResponse afterRestart = client(app).get()
					.uri("/api/v1/services/orders-api")
					.retrieve()
					.body(ServiceResponse.class);

				assertThat(afterRestart).isEqualTo(registered);
			}
		}
	}

	private static RestClient client(ConfigurableApplicationContext app) {
		String port = app.getEnvironment().getRequiredProperty("local.server.port");
		return RestClient.create("http://127.0.0.1:" + port);
	}

}
