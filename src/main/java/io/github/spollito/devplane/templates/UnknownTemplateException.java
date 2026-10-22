package io.github.spollito.devplane.templates;

import java.util.List;
import java.util.stream.Collectors;

import io.github.spollito.devplane.api.ApiExceptionHandler.InvalidField;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Rendered as a 400 problem details response whose {@code errors} array names the
 * {@code template} parameter, in the same shape as other validation errors.
 */
class UnknownTemplateException extends ErrorResponseException {

	UnknownTemplateException(String template, List<ServiceTemplate> listed) {
		super(HttpStatus.BAD_REQUEST, problem(template, listed), null);
	}

	private static ProblemDetail problem(String template, List<ServiceTemplate> listed) {
		String names = listed.stream().map(ServiceTemplate::name).collect(Collectors.joining(", "));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"No template named '" + template + "' is available.");
		problem.setProperty("errors", List.of(new InvalidField("template", "must be one of " + names)));
		return problem;
	}

}
