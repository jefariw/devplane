package io.github.spollito.devplane.templates;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * The templates that the Platform API offers. They are listed here rather than
 * discovered on the classpath, so test-only templates are never offered and every
 * template has a description.
 */
@Component
class ServiceTemplates {

	/** The golden path, used when a client doesn't choose a template. */
	static final String DEFAULT = "java-rest-api";

	private static final List<ServiceTemplate> TEMPLATES = List
		.of(new ServiceTemplate("java-rest-api", "Spring Boot (Java 21) REST API with PostgreSQL"));

	List<ServiceTemplate> all() {
		return TEMPLATES;
	}

	boolean isListed(String name) {
		return TEMPLATES.stream().anyMatch(template -> template.name().equals(name));
	}

}
