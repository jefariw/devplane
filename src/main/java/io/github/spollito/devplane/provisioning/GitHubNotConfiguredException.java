package io.github.spollito.devplane.provisioning;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Rendered as a 503 problem details response that says how to set up a token. */
class GitHubNotConfiguredException extends ErrorResponseException {

	GitHubNotConfiguredException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"GitHub isn't configured. Set DEVPLANE_GITHUB_TOKEN to a fine-grained personal access token and"
						+ " restart DevPlane."),
				null);
	}

}
