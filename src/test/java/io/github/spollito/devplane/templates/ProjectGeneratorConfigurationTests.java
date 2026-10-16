package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import io.github.spollito.devplane.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

/** The generator Spring creates uses the base package from application.properties. */
@IntegrationTest
class ProjectGeneratorConfigurationTests {

	@Autowired
	private ProjectGenerator generator;

	@Test
	void usesTheBasePackageFromConfiguration(@TempDir Path workspace) {
		Path project = workspace.resolve("project");

		generator.generate("test-template", new NewProject("orders-api", "payments-team", null), project);

		assertThat(project.resolve("README.md")).content().contains("Base package: io.github.spollito\n");
	}

}
