package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks the files generated from the real {@code java-rest-api} template. Whether
 * the generated project builds is checked by {@link GeneratedProjectBuildTests}.
 */
class JavaRestApiTemplateTests {

	@TempDir
	private Path workspace;

	private Path project;

	@BeforeEach
	void generateOrdersApi() {
		project = workspace.resolve("orders-api");
		new ProjectGenerator("io.github.spollito").generate("java-rest-api",
				new NewProject("orders-api", "payments-team", "Order management REST API"), project);
	}

	@Test
	void generatesAJavaRestApiProject() throws IOException {
		assertThat(project.resolve("settings.gradle.kts")).content().contains("rootProject.name = \"orders-api\"");
		assertThat(project.resolve("gradlew")).isExecutable();
		assertThat(project.resolve("gradle/wrapper/gradle-wrapper.jar")).exists();
		assertThat(project.resolve(".gitignore")).exists();
		assertThat(project.resolve("src/main/java/io/github/spollito/ordersapi/OrdersApiApplication.java")).content()
			.contains("public class OrdersApiApplication");
		assertThat(textFiles()).allSatisfy(file -> assertThat(Files.readString(file, StandardCharsets.UTF_8))
			.as("unfilled placeholder in %s", project.relativize(file))
			.doesNotContain("{{"));
	}

	private List<Path> textFiles() throws IOException {
		try (Stream<Path> files = Files.walk(project)) {
			return files.filter(Files::isRegularFile).filter(file -> !file.toString().endsWith(".jar")).toList();
		}
	}

}
