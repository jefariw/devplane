package io.github.spollito.devplane.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.net.URI;
import java.time.Instant;

import io.github.spollito.devplane.IntegrationTest;
import io.github.spollito.devplane.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Checks how the catalog records the repository other features provision. */
@IntegrationTest
class RecordRepositoryTests {

	private static final Instant REGISTERED_AT = Instant.parse("2026-02-10T08:15:00Z");

	private static final Instant PROVISIONED_AT = Instant.parse("2026-02-11T09:30:00.123456Z");

	private static final URI URL = URI.create("https://github.com/octo-dev/orders-api");

	@Autowired
	private ServiceCatalog catalog;

	@Autowired
	private ServiceRepository repository;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void registerOrdersApi() {
		repository.deleteAll();
		repository.save(new ServiceEntity("orders-api", "payments-team", null, Lifecycle.EXPERIMENTAL, REGISTERED_AT));
		clock.setInstant(PROVISIONED_AT);
	}

	@Test
	void recordsTheRepository() {
		SourceRepository recorded = catalog.recordRepository("orders-api", URL, "java-rest-api");

		assertThat(recorded).isEqualTo(new SourceRepository(URL, "java-rest-api", PROVISIONED_AT));
		assertThat(catalog.lookUp("orders-api").repository()).isEqualTo(recorded);
	}

	@Test
	void countsAsAnUpdate() {
		catalog.recordRepository("orders-api", URL, "java-rest-api");

		ServiceEntity service = repository.findByName("orders-api").orElseThrow();
		assertThat(service.getUpdatedAt()).isEqualTo(PROVISIONED_AT);
		assertThat(service.getCreatedAt()).isEqualTo(REGISTERED_AT);
	}

	@Test
	void rejectsAnUnknownService() {
		assertThatExceptionOfType(ServiceNotFoundException.class)
			.isThrownBy(() -> catalog.recordRepository("unknown-service", URL, "java-rest-api"));
	}

	@Test
	void recordsOnlyOneRepository() {
		catalog.recordRepository("orders-api", URL, "java-rest-api");

		assertThatIllegalStateException().isThrownBy(() -> catalog.recordRepository("orders-api",
				URI.create("https://github.com/octo-dev/other"), "java-rest-api"));
		assertThat(catalog.lookUp("orders-api").repository().url()).isEqualTo(URL);
	}

	@Test
	void databaseRejectsAPartialRepository() {
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> jdbc.sql("UPDATE services SET repository_url = :url WHERE name = 'orders-api'")
				.param("url", URL.toString())
				.update());
	}

}
