package io.github.spollito.devplane.templates;

import java.util.stream.Collectors;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

/**
 * Checks {@link ListedTemplate}. Spring creates constraint validators as beans, so
 * the template list is injected.
 */
class ListedTemplateValidator implements ConstraintValidator<ListedTemplate, String> {

	private final ServiceTemplates templates;

	ListedTemplateValidator(ServiceTemplates templates) {
		this.templates = templates;
	}

	@Override
	public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
		if (value == null || templates.isListed(value)) {
			return true;
		}
		// Same message as the download endpoint's unknown template error.
		String names = templates.all().stream().map(ServiceTemplate::name).collect(Collectors.joining(", "));
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate("must be one of " + names).addConstraintViolation();
		return false;
	}

}
