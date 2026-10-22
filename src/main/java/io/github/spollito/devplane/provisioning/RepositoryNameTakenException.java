package io.github.spollito.devplane.provisioning;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Rendered as a 409 problem details response. */
class RepositoryNameTakenException extends ErrorResponseException {

	RepositoryNameTakenException(String name) {
		super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The GitHub account already has a repository named '" + name + "'."), null);
	}

}
