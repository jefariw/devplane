package io.github.spollito.devplane.provisioning;

import io.github.spollito.devplane.templates.ListedTemplate;
import io.github.spollito.devplane.templates.ServiceTemplates;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code POST /api/v1/services/{name}/repository}. Both fields are
 * optional, and so is the body itself. {@code visibility} is a string so that an
 * unknown value is reported as a field error.
 */
record ProvisionRepositoryRequest(@ListedTemplate @Nullable String template,
		@Pattern(regexp = "private|public", message = "must be private or public") @Nullable String visibility) {

	static final ProvisionRepositoryRequest DEFAULTS = new ProvisionRepositoryRequest(null, null);

	String templateOrDefault() {
		return (template != null) ? template : ServiceTemplates.DEFAULT;
	}

	boolean isPrivate() {
		return !"public".equals(visibility);
	}

}
