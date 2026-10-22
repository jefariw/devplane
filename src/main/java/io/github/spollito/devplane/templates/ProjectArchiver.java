package io.github.spollito.devplane.templates;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

/**
 * Generates a project straight into a zip archive, in a folder named after the
 * service.
 * <p>
 * The archive is written through the JDK's zip file system with POSIX file
 * attributes enabled, so the generator's {@code Files} calls work unchanged, and
 * the execute permission it gives {@code gradlew} is stored as that entry's Unix
 * mode. No other entry gets a mode, so unzip tools apply their usual defaults.
 */
@Component
class ProjectArchiver {

	private static final Logger log = LoggerFactory.getLogger(ProjectArchiver.class);

	/**
	 * The default permissions are what the generator reads before it adds execute
	 * permissions to {@code gradlew}, which makes that entry {@code rwxr-xr-x}.
	 */
	private static final Map<String, String> ZIP_FILE_SYSTEM = Map.of("create", "true", "enablePosixFileAttributes",
			"true", "defaultPermissions", "rw-r--r--");

	private final ProjectGenerator generator;

	/** Where archives are written before they're read into memory. */
	private final Path temporaryDirectory;

	@Autowired
	ProjectArchiver(ProjectGenerator generator) {
		this(generator, Path.of(System.getProperty("java.io.tmpdir")));
	}

	ProjectArchiver(ProjectGenerator generator, Path temporaryDirectory) {
		this.generator = generator;
		this.temporaryDirectory = temporaryDirectory;
	}

	/**
	 * Generates {@code project} from the named template and returns it as a zip
	 * archive whose only top-level folder is the service name.
	 * @throws ProjectGenerationException if generation or writing the archive fails
	 */
	byte[] archive(String templateName, NewProject project) {
		Path workDirectory = createWorkDirectory();
		try {
			Path archive = workDirectory.resolve("project.zip");
			try (FileSystem zip = FileSystems.newFileSystem(archive, ZIP_FILE_SYSTEM)) {
				generator.generate(templateName, project, zip.getPath("/" + project.serviceName()));
			}
			return Files.readAllBytes(archive);
		}
		catch (IOException ex) {
			throw new ProjectGenerationException("Could not write the archive for " + project.serviceName(), ex);
		}
		finally {
			delete(workDirectory);
		}
	}

	private Path createWorkDirectory() {
		try {
			return Files.createTempDirectory(temporaryDirectory, "devplane-");
		}
		catch (IOException ex) {
			throw new ProjectGenerationException("Could not create a temporary directory in " + temporaryDirectory, ex);
		}
	}

	private static void delete(Path directory) {
		try {
			FileSystemUtils.deleteRecursively(directory);
		}
		catch (IOException ex) {
			// Too late to fail the request: the archive was read, or generation failed.
			log.warn("Could not delete temporary directory {}", directory, ex);
		}
	}

}
