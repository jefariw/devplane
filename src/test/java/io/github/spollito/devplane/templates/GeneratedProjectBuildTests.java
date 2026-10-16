package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.util.FileSystemUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The "Builds a freshly generated project" scenario: generates {@code orders-api}
 * from the {@code java-rest-api} template and runs that project's own build.
 * <p>
 * Tagged {@code template} so it runs in the {@code templateTest} task, not in
 * {@code test}. The generated project is left in {@code build/generated-projects/}
 * for inspection.
 */
@Tag("template")
class GeneratedProjectBuildTests {

	private static final Path PROJECT = Path.of("build/generated-projects/orders-api").toAbsolutePath();

	/** Tests the generated project must run and pass. Each covers a spec scenario. */
	private static final List<String> EXPECTED_TESTS = List.of("contextLoads", "createsAnItem", "listsItems",
			"returns404ForAnUnknownItem", "rejectsAnItemWithoutAName", "reportsHealthy", "reportsAnUnreachableDatabase",
			"servesTheApiDescription");

	@Test
	void buildsAFreshlyGeneratedProject() throws Exception {
		FileSystemUtils.deleteRecursively(PROJECT);
		new ProjectGenerator("io.github.spollito").generate("java-rest-api",
				new NewProject("orders-api", "payments-team", "Order management REST API"), PROJECT);

		Path log = PROJECT.resolveSibling("orders-api-build.log");
		Process build = new ProcessBuilder(PROJECT.resolve("gradlew").toString(), "build", "--console=plain")
			.directory(PROJECT.toFile())
			.redirectErrorStream(true)
			.redirectOutput(log.toFile())
			.start();
		boolean finished = build.waitFor(10, TimeUnit.MINUTES);
		if (!finished) {
			build.destroyForcibly();
		}

		assertThat(finished).as("generated project's build finished within 10 minutes").isTrue();
		assertThat(build.exitValue()).as("generated project's build failed:%n%s", tail(log)).isZero();
		assertThat(passedTests()).containsAll(EXPECTED_TESTS);
	}

	/** Names of the generated project's tests that ran without failing or being skipped. */
	private static List<String> passedTests() throws Exception {
		List<String> passed = new ArrayList<>();
		try (Stream<Path> reports = Files.list(PROJECT.resolve("build/test-results/test"))) {
			for (Path report : reports.filter(path -> path.toString().endsWith(".xml")).toList()) {
				NodeList testCases = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder()
					.parse(report.toFile())
					.getElementsByTagName("testcase");
				for (int i = 0; i < testCases.getLength(); i++) {
					Element testCase = (Element) testCases.item(i);
					if (testCase.getElementsByTagName("failure").getLength() == 0
							&& testCase.getElementsByTagName("error").getLength() == 0
							&& testCase.getElementsByTagName("skipped").getLength() == 0) {
						passed.add(testCase.getAttribute("name").replace("()", ""));
					}
				}
			}
		}
		return passed;
	}

	private static String tail(Path log) throws IOException {
		List<String> lines = Files.readAllLines(log);
		return String.join("\n", lines.subList(Math.max(0, lines.size() - 60), lines.size()));
	}

}
