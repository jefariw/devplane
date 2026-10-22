package io.github.spollito.devplane.provisioning;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * GitHub failed or couldn't be reached. Rendered as a 502 problem details response
 * whose detail DevPlane writes itself, never copied from GitHub or Git.
 */
class GitHubException extends ErrorResponseException {

	GitHubException(String detail, @Nullable Throwable cause) {
		super(HttpStatus.BAD_GATEWAY, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, detail), cause);
	}

}
