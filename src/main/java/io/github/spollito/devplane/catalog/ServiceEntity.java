package io.github.spollito.devplane.catalog;

import java.net.URI;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A service registered in the catalog. Named {@code ServiceEntity} because
 * "service" would collide with Spring's {@code @Service} stereotype.
 */
@Entity
@Table(name = "services")
class ServiceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private String name;

	@Column(nullable = false)
	private String owner;

	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Lifecycle lifecycle;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	private String repositoryUrl;

	private String repositoryTemplate;

	private Instant repositoryCreatedAt;

	protected ServiceEntity() {
		// for JPA
	}

	ServiceEntity(String name, String owner, String description, Lifecycle lifecycle, Instant now) {
		this.name = name;
		this.owner = owner;
		this.description = description;
		this.lifecycle = lifecycle;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void update(String owner, String description, Lifecycle lifecycle, Instant now) {
		this.owner = owner;
		this.description = description;
		this.lifecycle = lifecycle;
		this.updatedAt = now;
	}

	/**
	 * Records the service's repository, which also counts as an update.
	 * @throws IllegalStateException if the service already has a repository
	 */
	void recordRepository(URI url, String template, Instant now) {
		if (this.repositoryUrl != null) {
			throw new IllegalStateException("Service '" + this.name + "' already has a repository");
		}
		this.repositoryUrl = url.toString();
		this.repositoryTemplate = template;
		this.repositoryCreatedAt = now;
		this.updatedAt = now;
	}

	String getName() {
		return name;
	}

	String getOwner() {
		return owner;
	}

	String getDescription() {
		return description;
	}

	Lifecycle getLifecycle() {
		return lifecycle;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

	/** The service's repository, or {@code null} if none was provisioned. */
	SourceRepository getRepository() {
		return (repositoryUrl != null)
				? new SourceRepository(URI.create(repositoryUrl), repositoryTemplate, repositoryCreatedAt) : null;
	}

}
