package io.github.spollito.devplane.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import io.github.spollito.devplane.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Scenarios from the platform-api spec's "OpenAPI documentation" requirement. */
@IntegrationTest
class ApiDocumentationTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void servesTheOpenApiDescription() {
		MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

		assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
		assertThat(result).bodyJson().extractingPath("$.openapi").asString().startsWith("3.");
		assertThat(result).bodyJson()
			.extractingPath("$.paths")
			.asMap()
			.satisfies(paths -> assertThat(paths.keySet()).containsAll(apiEndpointPaths()));
	}

	@Test
	void servesTheDocumentationPage() {
		MvcTestResult redirect = mvc.get().uri("/swagger-ui.html").exchange();
		assertThat(redirect).hasStatus3xxRedirection();
		String location = redirect.getResponse().getHeader("Location");
		assertThat(location).endsWith("/swagger-ui/index.html");

		assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk()
			.hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
			.bodyText()
			.contains("swagger-ui");
	}

	/** Every path the application actually serves under /api/v1. */
	private Set<String> apiEndpointPaths() {
		return handlerMapping.getHandlerMethods()
			.keySet()
			.stream()
			.flatMap(mapping -> mapping.getPatternValues().stream())
			.filter(path -> path.startsWith("/api/v1/"))
			.collect(Collectors.toSet());
	}

}
