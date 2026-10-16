package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringBootVersion;

/**
 * The generator's rules, exercised with small test-only templates in
 * {@code src/test/resources/service-templates/}. Covers the "Generate a project
 * from a template" scenarios that don't depend on the real template.
 */
class ProjectGeneratorTests {

	private static final NewProject ORDERS_API = new NewProject("orders-api", "payments-team", "Order management");

	private final ProjectGenerator generator = new ProjectGenerator("io.github.spollito");

	@TempDir
	private Path workspace;

	@Test
	void fillsPlaceholdersInContentsAndPaths() throws IOException {
		Path project = generate("test-template", ORDERS_API);

		assertThat(project.resolve("README.md")).content().isEqualTo("""
				# orders-api

				Owner: payments-team
				Description: Order management
				Package: io.github.spollito.ordersapi (io/github/spollito/ordersapi)
				Class: OrdersApiApplication
				Database: orders_api
				Base package: io.github.spollito
				Spring Boot: %s
				""".formatted(SpringBootVersion.getVersion()));
		assertThat(project.resolve("src/main/java/io/github/spollito/ordersapi/OrdersApiApplication.java")).content()
			.startsWith("package io.github.spollito.ordersapi;");
	}

	@Test
	void usesTheConfiguredBasePackage() {
		ProjectGenerator generator = new ProjectGenerator("com.example.platform");
		Path project = workspace.resolve("project");

		generator.generate("test-template", ORDERS_API, project);

		assertThat(project.resolve("README.md")).content()
			.contains("Package: com.example.platform.ordersapi (com/example/platform/ordersapi)");
		assertThat(project.resolve("src/main/java/com/example/platform/ordersapi/OrdersApiApplication.java")).exists();
	}

	@ParameterizedTest
	@ValueSource(strings = { "io.github.", "io.github.spollito.class", "io github", "" })
	void rejectsAnInvalidBasePackage(String basePackage) {
		assertThatIllegalArgumentException().isThrownBy(() -> new ProjectGenerator(basePackage));
	}

	@Test
	void usesADefaultDescriptionWhenNoneIsGiven() {
		Path project = generate("test-template", new NewProject("orders-api", "payments-team", null));

		assertThat(project.resolve("README.md")).content().contains("Description: No description provided.");
	}

	@Test
	void restoresDotfiles() {
		Path project = generate("test-template", ORDERS_API);

		assertThat(project.resolve(".gitignore")).hasContent("build/");
		assertThat(project.resolve("_gitignore")).doesNotExist();
	}

	@Test
	void keepsNamesThatContainDoubleUnderscores() {
		Path project = generate("test-template", ORDERS_API);

		assertThat(project.resolve("db/V1__create_things_table.sql")).exists();
		assertThat(project.resolve("scripts/__init__.py")).exists();
	}

	@Test
	void copiesJarFilesUnchanged() throws IOException {
		Path project = generate("test-template", ORDERS_API);

		try (InputStream original = getClass()
			.getResourceAsStream("/service-templates/test-template/lib/sample.jar")) {
			assertThat(project.resolve("lib/sample.jar")).hasBinaryContent(original.readAllBytes());
		}
	}

	@Test
	void makesGradlewExecutable() {
		Path project = generate("test-template", ORDERS_API);

		assertThat(project.resolve("gradlew")).isExecutable();
	}

	@Test
	void generatesTheSameFilesForTheSameInputs() {
		Path first = generate("test-template", ORDERS_API, "first");
		Path second = generate("test-template", ORDERS_API, "second");

		assertThat(filesIn(second)).isEqualTo(filesIn(first));
	}

	@Test
	void refusesANonEmptyTargetDirectory() throws IOException {
		Path target = Files.createDirectories(workspace.resolve("occupied"));
		Files.writeString(target.resolve("existing.txt"), "keep me");

		assertThatExceptionOfType(ProjectGenerationException.class)
			.isThrownBy(() -> generator.generate("test-template", ORDERS_API, target))
			.withMessageContaining("not empty");
		assertThat(filesIn(target)).containsOnlyKeys("existing.txt");
	}

	@ParameterizedTest
	@ValueSource(strings = { "cobol-batch", "../service-templates/test-template", "Test-Template" })
	void rejectsAnUnknownTemplate(String templateName) {
		Path target = workspace.resolve("project");

		assertThatExceptionOfType(ProjectGenerationException.class)
			.isThrownBy(() -> generator.generate(templateName, ORDERS_API, target))
			.withMessageContaining("Unknown template");
		assertThat(target).doesNotExist();
	}

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = { "Orders API, payments-team, Invalid service name",
			"null, payments-team, Invalid service name", "orders-api, Payments Team, Invalid owner" })
	void rejectsAnInvalidServiceName(String serviceName, String owner, String message) {
		Path target = workspace.resolve("project");

		assertThatExceptionOfType(ProjectGenerationException.class)
			.isThrownBy(() -> generator.generate("test-template", new NewProject(serviceName, owner, null), target))
			.withMessageContaining(message);
		assertThat(target).doesNotExist();
	}

	@Test
	void rejectsAnOverlongDescription() {
		Path target = workspace.resolve("project");

		assertThatExceptionOfType(ProjectGenerationException.class).isThrownBy(() -> generator
			.generate("test-template", new NewProject("orders-api", "payments-team", "x".repeat(301)), target));
		assertThat(target).doesNotExist();
	}

	@ParameterizedTest
	@CsvSource({ "broken-content, Unknown placeholder {{unknownVariable}}",
			"broken-path, Unknown placeholder {{unknownToken}}", "malformed, Malformed placeholder" })
	void failsOnALeftoverPlaceholderWithoutWritingAnything(String templateName, String message) {
		Path target = workspace.resolve("project");

		assertThatExceptionOfType(ProjectGenerationException.class)
			.isThrownBy(() -> generator.generate(templateName, ORDERS_API, target))
			.withMessageContaining(message);
		assertThat(target).doesNotExist();
	}

	private Path generate(String templateName, NewProject project) {
		return generate(templateName, project, "project");
	}

	private Path generate(String templateName, NewProject project, String directory) {
		Path target = workspace.resolve(directory);
		generator.generate(templateName, project, target);
		return target;
	}

	/** Every file under {@code directory}, by relative path, with its content. */
	private static Map<String, String> filesIn(Path directory) {
		try (Stream<Path> paths = Files.walk(directory)) {
			Map<String, String> files = new TreeMap<>();
			paths.filter(Files::isRegularFile).forEach(path -> {
				try {
					files.put(directory.relativize(path).toString(),
							new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1));
				}
				catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			});
			return files;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
