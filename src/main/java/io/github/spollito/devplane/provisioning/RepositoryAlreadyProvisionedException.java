package io.github.spollito.devplane.provisioning;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Rendered as a 409 problem details response. */
class RepositoryAlreadyProvisionedException extends ErrorResponseException {

	RepositoryAlreadyProvisionedException(String name, URI url) {
		super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Service '" + name + "' already has a repository: " + url), null);
	}

}
