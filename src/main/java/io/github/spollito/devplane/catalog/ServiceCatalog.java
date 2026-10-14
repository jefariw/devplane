package io.github.spollito.devplane.catalog;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The catalog's business rules: registering services and keeping their details. */
@Service
@Transactional
class ServiceCatalog {

	/** PostgreSQL's SQLSTATE for a unique constraint violation. */
	private static final String UNIQUE_VIOLATION = "23505";

	private final ServiceRepository repository;

	private final Clock clock;

	ServiceCatalog(ServiceRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * Registers a new service, defaulting its lifecycle to experimental.
	 * @throws DuplicateServiceNameException if a service with this name exists
	 */
	ServiceEntity register(String name, String owner, @Nullable String description, @Nullable Lifecycle lifecycle) {
		ServiceEntity service = new ServiceEntity(name, owner, description,
				(lifecycle != null) ? lifecycle : Lifecycle.EXPERIMENTAL, now());
		try {
			return repository.saveAndFlush(service);
		}
		catch (DataIntegrityViolationException ex) {
			// The name's unique constraint is the only one an insert can violate, and
			// relying on it (rather than checking first) also holds when requests race.
			if (ex.getMostSpecificCause() instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
				throw new DuplicateServiceNameException(name, ex);
			}
			throw ex;
		}
	}

	/**
	 * Returns the service with the given name.
	 * @throws ServiceNotFoundException if no service has this name
	 */
	@Transactional(readOnly = true)
	ServiceEntity get(String name) {
		return repository.findByName(name).orElseThrow(() -> new ServiceNotFoundException(name));
	}

	/** Lists services one page at a time, optionally only those with the given owner. */
	@Transactional(readOnly = true)
	Page<ServiceEntity> list(@Nullable String owner, Pageable pageable) {
		return (owner != null) ? repository.findByOwner(owner, pageable) : repository.findAll(pageable);
	}

	/**
	 * Replaces a service's owner, description, and lifecycle. The name never changes.
	 * @throws ServiceNotFoundException if no service has this name
	 */
	ServiceEntity update(String name, String owner, @Nullable String description, Lifecycle lifecycle) {
		ServiceEntity service = get(name);
		service.update(owner, description, lifecycle, now());
		return service;
	}

	/**
	 * Removes a service from the catalog.
	 * @throws ServiceNotFoundException if no service has this name
	 */
	void unregister(String name) {
		repository.delete(get(name));
	}

	/**
	 * The current time at the database's precision. PostgreSQL stores microseconds, so
	 * finer precision would make a saved service differ from the same service read back.
	 */
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

}
