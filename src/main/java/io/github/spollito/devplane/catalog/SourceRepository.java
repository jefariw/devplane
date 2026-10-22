package io.github.spollito.devplane.catalog;

import java.net.URI;
import java.time.Instant;

/**
 * The GitHub repository that holds a service's code. Named {@code SourceRepository}
 * because "repository" already means the catalog's Spring Data repository.
 *
 * @param url the repository's page on GitHub
 * @param template the template its first commit was generated from
 * @param createdAt when DevPlane created it
 */
public record SourceRepository(URI url, String template, Instant createdAt) {

}
