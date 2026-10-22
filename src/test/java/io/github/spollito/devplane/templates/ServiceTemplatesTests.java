package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the list of templates that the Platform API offers. */
class ServiceTemplatesTests {

	private final ServiceTemplates templates = new ServiceTemplates();

	@TempDir
	private Path workspace;

	@Test
	void everyListedTemplateGenerates() {
		ProjectGenerator generator = new ProjectGenerator("io.github.spollito");

		assertThat(templates.all()).isNotEmpty().allSatisfy(template -> {
			assertThat(template.description()).isNotBlank();
			Path project = workspace.resolve(template.name());
			generator.generate(template.name(), new NewProject("orders-api", "payments-team", null), project);
			assertThat(project).isNotEmptyDirectory();
		});
	}

	@Test
	void listsTheDefaultTemplate() {
		assertThat(templates.isListed(ServiceTemplates.DEFAULT)).isTrue();
	}

	@Test
	void offersNoOtherTemplates() {
		assertThat(templates.isListed("test-template")).as("a test-only template").isFalse();
		assertThat(templates.isListed("cobol-batch")).isFalse();
	}

}
