package io.github.spollito.devplane.templates;

import org.jspecify.annotations.Nullable;

/** What a project is generated for: a service's name, its owner, and an optional description. */
public record NewProject(String serviceName, String owner, @Nullable String description) {

}
