package io.github.spollito.devplane.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.spollito.devplane.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Checks that the OpenAPI description covers every catalog endpoint and its bodies. */
@IntegrationTest
class CatalogDocumentationTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void documentsAllFiveCatalogEndpoints() {
		MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson()
			.extractingPath("$.paths['/api/v1/services']")
			.asMap()
			.containsOnlyKeys("get", "post");
		assertThat(result).bodyJson()
			.extractingPath("$.paths['/api/v1/services/{name}']")
			.asMap()
			.containsOnlyKeys("get", "put", "delete");
		assertThat(result).bodyJson()
			.extractingPath("$.components.schemas")
			.asMap()
			.containsKeys("RegisterServiceRequest", "UpdateServiceRequest", "ServiceResponse");
	}

}
