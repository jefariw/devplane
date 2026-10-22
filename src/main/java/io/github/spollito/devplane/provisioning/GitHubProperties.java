package io.github.spollito.devplane.provisioning;

import java.net.URI;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How DevPlane reaches GitHub. Spring binds the {@code DEVPLANE_GITHUB_TOKEN}
 * environment variable to {@code token}.
 *
 * @param token a fine-grained personal access token, or {@code null} when
 * provisioning isn't set up
 * @param apiUrl the root of GitHub's REST API
 */
@ConfigurationProperties("devplane.github")
record GitHubProperties(@Nullable String token, URI apiUrl) {

	boolean hasToken() {
		return token != null && !token.isBlank();
	}

	/** Leaves the token out, so that logging these properties never leaks it. */
	@Override
	public String toString() {
		return "GitHubProperties[apiUrl=" + apiUrl + ", token=" + (hasToken() ? "(set)" : "(not set)") + "]";
	}

}
