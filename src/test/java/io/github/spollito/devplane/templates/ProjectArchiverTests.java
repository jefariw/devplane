package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the zip archives that generated projects are downloaded as. */
class ProjectArchiverTests {

	private static final NewProject ORDERS_API = new NewProject("orders-api", "payments-team",
			"Order management REST API");

	private final ProjectGenerator generator = new ProjectGenerator("io.github.spollito");

	@TempDir
	private Path workspace;

	@TempDir
	private Path temporaryDirectory;

	@Test
	void holdsTheSameFilesAsGeneratingIntoADirectory() throws IOException {
		Path directory = workspace.resolve("orders-api");
		generator.generate("java-rest-api", ORDERS_API, directory);

		byte[] archive = archiver().archive("java-rest-api", ORDERS_API);

		assertThat(ProjectArchives.files(archive)).isNotEmpty()
			.isEqualTo(ProjectArchives.files(directory, "orders-api/"));
	}

	@Test
	void marksOnlyGradlewAsExecutable() throws IOException {
		byte[] archive = archiver().archive("java-rest-api", ORDERS_API);

		Map<String, String> modes = ProjectArchives.unixModes(archive, workspace);
		assertThat(modes.remove("orders-api/gradlew")).isEqualTo("rwxr-xr-x");
		assertThat(modes).as("entries that unzip tools give their default permissions")
			.isNotEmpty()
			.allSatisfy((path, mode) -> assertThat(mode).as(path).isEqualTo("---------"));
	}

	@Test
	void leavesNoTemporaryFiles() {
		archiver().archive("java-rest-api", ORDERS_API);

		assertThat(temporaryDirectory).isEmptyDirectory();
	}

	@Test
	void leavesNoTemporaryFilesWhenGenerationFails() {
		assertThatExceptionOfType(ProjectGenerationException.class)
			.isThrownBy(() -> archiver().archive("cobol-batch", ORDERS_API));

		assertThat(temporaryDirectory).isEmptyDirectory();
	}

	private ProjectArchiver archiver() {
		return new ProjectArchiver(generator, temporaryDirectory);
	}

}
