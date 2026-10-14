package io.github.spollito.devplane.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.spollito.devplane.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The "Healthy" scenario from the platform-api spec's "Health check" requirement. */
@IntegrationTest
class HealthCheckTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void healthy() {
		assertThat(mvc.get().uri("/actuator/health")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("UP");
	}

}
