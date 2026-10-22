package io.github.spollito.devplane.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;

import io.github.spollito.devplane.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class ServiceRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

	@Autowired
	private ServiceRepository repository;

	@BeforeEach
	void emptyCatalog() {
		repository.deleteAll();
	}

	@Test
	void savesAndReloadsAService() {
		repository.saveAndFlush(
				new ServiceEntity("orders-api", "payments-team", "Order management REST API", Lifecycle.PRODUCTION, NOW));

		ServiceEntity reloaded = repository.findByName("orders-api").orElseThrow();

		assertThat(reloaded.getOwner()).isEqualTo("payments-team");
		assertThat(reloaded.getDescription()).isEqualTo("Order management REST API");
		assertThat(reloaded.getLifecycle()).isEqualTo(Lifecycle.PRODUCTION);
		assertThat(reloaded.getCreatedAt()).isEqualTo(NOW);
		assertThat(reloaded.getUpdatedAt()).isEqualTo(NOW);
		assertThat(reloaded.getRepository()).isNull();
	}

	@Test
	void databaseRejectsADuplicateName() {
		repository.saveAndFlush(new ServiceEntity("orders-api", "payments-team", null, Lifecycle.EXPERIMENTAL, NOW));

		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> repository
			.saveAndFlush(new ServiceEntity("orders-api", "checkout-team", null, Lifecycle.EXPERIMENTAL, NOW)));
	}

}
