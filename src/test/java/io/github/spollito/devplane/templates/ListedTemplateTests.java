package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.spollito.devplane.IntegrationTest;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/** Checks {@link ListedTemplate} through the application's own validator. */
@IntegrationTest
class ListedTemplateTests {

	@Autowired
	private Validator validator;

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = "java-rest-api")
	void acceptsAListedTemplateOrNone(String template) {
		assertThat(validator.validate(new Choice(template))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "cobol-batch", "test-template", "" })
	void rejectsATemplateThatIsNotListed(String template) {
		assertThat(validator.validate(new Choice(template))).singleElement().satisfies(violation -> {
			assertThat(violation.getPropertyPath()).hasToString("template");
			assertThat(violation.getMessage()).isEqualTo("must be one of java-rest-api");
		});
	}

	record Choice(@ListedTemplate String template) {
	}

}
