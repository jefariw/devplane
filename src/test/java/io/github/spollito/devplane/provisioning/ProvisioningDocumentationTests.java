package io.github.spollito.devplane.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.spollito.devplane.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Checks that the OpenAPI description covers the provisioning endpoint. */
@IntegrationTest
class ProvisioningDocumentationTests {

	private static final String OPERATION = "$.paths['/api/v1/services/{name}/repository'].post";

	@Autowired
	private MockMvcTester mvc;

	@Test
	void documentsTheProvisioningEndpoint() {
		MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson()
			.extractingPath("$.paths['/api/v1/services/{name}/repository']")
			.asMap()
			.containsOnlyKeys("post");
		// OpenAPI treats a request body without "required": true as optional.
		assertThat(result).bodyJson().extractingPath(OPERATION + ".requestBody").asMap().doesNotContainKey("required");
		assertThat(result).bodyJson()
			.extractingPath(OPERATION + ".requestBody.content['application/json'].schema.$ref")
			.isEqualTo("#/components/schemas/ProvisionRepositoryRequest");
		assertThat(result).bodyJson()
			.extractingPath(OPERATION + ".responses['201'].content['*/*'].schema.$ref")
			.isEqualTo("#/components/schemas/SourceRepository");
	}

}
