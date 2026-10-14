package io.github.spollito.devplane.catalog;

import java.time.Instant;

/** A service as the Platform API returns it. */
record ServiceResponse(String name, String owner, String description, String lifecycle, Instant createdAt,
		Instant updatedAt) {

	static ServiceResponse from(ServiceEntity service) {
		return new ServiceResponse(service.getName(), service.getOwner(), service.getDescription(),
				service.getLifecycle().apiValue(), service.getCreatedAt(), service.getUpdatedAt());
	}

}
