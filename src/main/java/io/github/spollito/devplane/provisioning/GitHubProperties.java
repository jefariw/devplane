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

	/**
	 * Strips whitespace around the token, such as the line break that copying it
	 * often adds. No token contains whitespace, and an HTTP header can't hold a
	 * line break.
	 */
	GitHubProperties {
		token = (token != null) ? token.strip() : null;
	}

	boolean hasToken() {
		return token != null && !token.isEmpty();
	}

	/** Leaves the token out, so that logging these properties never leaks it. */
	@Override
	public String toString() {
		return "GitHubProperties[apiUrl=" + apiUrl + ", token=" + (hasToken() ? "(set)" : "(not set)") + "]";
	}

}
