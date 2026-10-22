package io.github.spollito.devplane.catalog;

import org.jspecify.annotations.Nullable;

/**
 * A registered service as other DevPlane features see it: a read-only copy of its
 * details, without the catalog's persistence.
 */
public record RegisteredService(String name, String owner, @Nullable String description) {

	static RegisteredService from(ServiceEntity service) {
		return new RegisteredService(service.getName(), service.getOwner(), service.getDescription());
	}

}
