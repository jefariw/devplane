package io.github.spollito.devplane.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import io.github.spollito.devplane.IntegrationTest;
import io.github.spollito.devplane.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Scenarios from the service-catalog spec's "Update a service" and "Unregister a service" requirements. */
@IntegrationTest
class UpdateAndUnregisterServicesTests {

	private static final Instant REGISTERED_AT = Instant.parse("2026-02-10T08:15:00Z");

	private static final Instant UPDATED_AT = Instant.parse("2026-04-20T16:45:00Z");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private ServiceRepository repository;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void registerOrdersApi() {
		repository.deleteAll();
		repository.save(new ServiceEntity("orders-api", "payments-team", "Order management REST API",
				Lifecycle.EXPERIMENTAL, REGISTERED_AT));
		clock.setInstant(UPDATED_AT);
	}

	// Update a service

	@Test
	void updatesAService() {
		MvcTestResult result = update("orders-api", """
				{"owner": "checkout-team", "lifecycle": "production"}
				""");

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{
				  "name": "orders-api",
				  "owner": "checkout-team",
				  "description": null,
				  "lifecycle": "production",
				  "createdAt": "2026-02-10T08:15:00Z",
				  "updatedAt": "2026-04-20T16:45:00Z"
				}
				""");
		ServiceEntity stored = repository.findByName("orders-api").orElseThrow();
		assertThat(stored.getOwner()).isEqualTo("checkout-team");
		assertThat(stored.getDescription()).as("an omitted description clears it").isNull();
		assertThat(stored.getUpdatedAt()).isEqualTo(UPDATED_AT);
	}

	@Test
	void rejectsAnInvalidUpdate() {
		MvcTestResult result = update("orders-api", """
				{"owner": "checkout-team"}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("lifecycle");
		ServiceEntity stored = repository.findByName("orders-api").orElseThrow();
		assertThat(stored.getOwner()).isEqualTo("payments-team");
		assertThat(stored.getUpdatedAt()).isEqualTo(REGISTERED_AT);
	}

	@Test
	void rejectsAnUpdateToAnUnknownService() {
		assertThat(update("unknown-service", """
				{"owner": "checkout-team", "lifecycle": "production"}
				""")).hasStatus(HttpStatus.NOT_FOUND);
	}

	// Unregister a service

	@Test
	void removesAService() {
		assertThat(mvc.delete().uri("/api/v1/services/orders-api")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(mvc.get().uri("/api/v1/services/orders-api")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/api/v1/services")).bodyJson().extractingPath("$.items").asArray().isEmpty();
	}

	@Test
	void rejectsDeletingAnUnknownService() {
		assertThat(mvc.delete().uri("/api/v1/services/unknown-service")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(repository.count()).isOne();
	}

	private MvcTestResult update(String name, String json) {
		return mvc.put()
			.uri("/api/v1/services/{name}", name)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

}
