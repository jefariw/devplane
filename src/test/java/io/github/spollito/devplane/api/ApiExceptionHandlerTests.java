package io.github.spollito.devplane.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scenarios from the platform-api spec's "Errors use problem details" and
 * "Validation errors list every invalid field" requirements, exercised through
 * a test-only controller so they don't depend on any feature's endpoints.
 */
// Component scanning skips classes nested in tests, so the controller is imported
// explicitly. Naming it in @WebMvcTest keeps the application's controllers out.
@WebMvcTest(controllers = ApiExceptionHandlerTests.TestOnlyController.class)
@Import(ApiExceptionHandlerTests.TestOnlyController.class)
class ApiExceptionHandlerTests {

	private static final String INTERNAL_DETAIL = "connection pool exhausted on db-host-42";

	@Autowired
	private MockMvcTester mvc;

	@Test
	void unknownEndpoint() {
		MvcTestResult result = mvc.get().uri("/api/v1/does-not-exist").exchange();

		assertProblemDetails(result, HttpStatus.NOT_FOUND);
	}

	@Test
	void malformedRequestBody() {
		MvcTestResult result = mvc.post()
			.uri("/api/v1/test-only/echo")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": ")
			.exchange();

		assertProblemDetails(result, HttpStatus.BAD_REQUEST);
	}

	@Test
	void unexpectedErrorsHideInternals() {
		MvcTestResult result = mvc.get().uri("/api/v1/test-only/fail").exchange();

		assertProblemDetails(result, HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(result).bodyText()
			.doesNotContain(INTERNAL_DETAIL)
			.doesNotContain("IllegalStateException")
			.doesNotContain("at io.github.spollito");
	}

	@Test
	void reportsEveryInvalidField() {
		MvcTestResult result = mvc.post()
			.uri("/api/v1/test-only/echo")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "", "count": 0}
					""")
			.exchange();

		assertProblemDetails(result, HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("count", "name");
		assertThat(result).bodyJson()
			.extractingPath("$.errors[*].message")
			.asArray()
			.hasSize(2)
			.allSatisfy(message -> assertThat(message).asString().isNotBlank());
	}

	private static void assertProblemDetails(MvcTestResult result, HttpStatus status) {
		assertThat(result).hasStatus(status).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().hasPath("$.type").hasPath("$.title").hasPath("$.detail");
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
	}

	@RestController
	static class TestOnlyController {

		@PostMapping("/api/v1/test-only/echo")
		EchoRequest echo(@Valid @RequestBody EchoRequest request) {
			return request;
		}

		@GetMapping("/api/v1/test-only/fail")
		String fail() {
			throw new IllegalStateException(INTERNAL_DETAIL);
		}

	}

	record EchoRequest(@NotBlank String name, @Positive int count) {
	}

}
