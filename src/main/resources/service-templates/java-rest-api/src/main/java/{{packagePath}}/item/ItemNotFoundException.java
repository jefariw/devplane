package {{packageName}}.item;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Rendered as a 404 problem details response by the API's exception handler. */
class ItemNotFoundException extends ErrorResponseException {

	ItemNotFoundException(long id) {
		super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No item with id " + id + "."),
				null);
	}

}
