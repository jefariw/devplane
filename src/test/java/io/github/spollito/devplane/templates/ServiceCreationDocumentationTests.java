package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.spollito.devplane.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Checks that the OpenAPI description covers the service creation endpoints and their bodies. */
@IntegrationTest
class ServiceCreationDocumentationTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void documentsTheTemplateList() {
		MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.paths['/api/v1/templates']").asMap().containsOnlyKeys("get");
		assertThat(result).bodyJson().extractingPath("$.components.schemas").asMap().containsKey("ServiceTemplate");
	}

	@Test
	void documentsTheProjectDownload() {
		MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson()
			.extractingPath("$.paths['/api/v1/services/{name}/project'].get.parameters[*].name")
			.asArray()
			.containsExactlyInAnyOrder("name", "template");
		assertThat(result).bodyJson()
			.extractingPath("$.paths['/api/v1/services/{name}/project'].get.responses['200'].content")
			.asMap()
			.containsOnlyKeys("application/zip");
	}

}
