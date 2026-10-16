package {{packageName}}.api;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every API error as an RFC 9457 problem details document. The base class
 * covers Spring MVC's own exceptions and any {@code ErrorResponseException}; this
 * class always includes {@code type} and turns unexpected exceptions into a
 * generic 500 that never exposes their details.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	/** RFC 9457's default problem type, which Spring omits unless it is set. */
	private static final URI DEFAULT_TYPE = URI.create("about:blank");

	@Override
	protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ProblemDetail problem) {
			withDefaultType(problem);
		}
		return super.createResponseEntity(body, headers, statusCode, request);
	}

	/** Adds an {@code errors} array naming each invalid field. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<InvalidField> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new InvalidField(error.getField(), error.getDefaultMessage()))
			.sorted(Comparator.comparing(InvalidField::field))
			.toList();
		ProblemDetail body = ex.getBody();
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error while handling a request", ex);
		return withDefaultType(
				ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."));
	}

	private static ProblemDetail withDefaultType(ProblemDetail problem) {
		if (problem.getType() == null) {
			problem.setType(DEFAULT_TYPE);
		}
		return problem;
	}

	/** One entry in the {@code errors} array of a validation failure. */
	public record InvalidField(String field, String message) {
	}

}
