package io.github.spollito.devplane.templates;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated string must name a template that {@code GET /api/v1/templates}
 * lists. {@code null} is valid, so that the field can be optional.
 */
@Documented
@Constraint(validatedBy = ListedTemplateValidator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface ListedTemplate {

	String message() default "must be a template listed by GET /api/v1/templates";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
