package io.github.spollito.devplane.catalog;

import org.jspecify.annotations.Nullable;

/**
 * A registered service as other DevPlane features see it: a read-only copy of its
 * details, without the catalog's persistence.
 *
 * @param repository the service's repository, or {@code null} if none was
 * provisioned
 */
public record RegisteredService(String name, String owner, @Nullable String description,
		@Nullable SourceRepository repository) {

	static RegisteredService from(ServiceEntity service) {
		return new RegisteredService(service.getName(), service.getOwner(), service.getDescription(),
				service.getRepository());
	}

}
