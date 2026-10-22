package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.spollito.devplane.IntegrationTest;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Scenarios from the service-creation spec's "List available templates" requirement. */
@IntegrationTest
class ListTemplatesTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void listsTheJavaRestApiTemplate() {
		MvcTestResult result = mvc.get().uri("/api/v1/templates").exchange();

		assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
		assertThat(result).bodyJson().extractingPath("$").asArray().isNotEmpty();
		assertThat(result).bodyJson()
			.extractingPath("$[?(@.name == 'java-rest-api')].description")
			.asArray()
			.singleElement(InstanceOfAssertFactories.STRING)
			.isNotBlank();
	}

}
