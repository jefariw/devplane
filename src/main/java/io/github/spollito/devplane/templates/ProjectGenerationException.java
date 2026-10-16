package io.github.spollito.devplane.templates;

/** Generation failed. When thrown before writing starts, nothing was written. */
public class ProjectGenerationException extends RuntimeException {

	ProjectGenerationException(String message) {
		super(message);
	}

	ProjectGenerationException(String message, Throwable cause) {
		super(message, cause);
	}

}
