package io.github.spollito.devplane.templates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.lang.model.SourceVersion;

import io.github.spollito.devplane.catalog.CatalogConstraints;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * Generates a project from a template stored on the classpath under
 * {@code service-templates/<template-name>/}.
 * <p>
 * Templates use {@code {{variable}}} in file contents and file paths. A file name
 * starting with a single {@code _} is a dotfile ({@code _gitignore} becomes
 * {@code .gitignore}, while {@code __init__.py} is left alone), {@code .jar} files
 * are copied unchanged, and {@code gradlew} is made executable. Every file is
 * rendered before anything is written, so a failure leaves the target directory
 * untouched.
 */
@Component
public class ProjectGenerator {

	private static final String TEMPLATES_ROOT = "service-templates/";

	private static final Pattern TEMPLATE_NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z]+)}}");

	private static final Pattern IDENTIFIER = Pattern.compile(CatalogConstraints.IDENTIFIER_PATTERN);

	private static final String NO_DESCRIPTION = "No description provided.";

	private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
			ProjectGenerator.class.getClassLoader());

	private final String basePackage;

	/** New services start on the Spring Boot version DevPlane itself runs. */
	private final String springBootVersion;

	public ProjectGenerator(@Value("${devplane.templates.base-package}") String basePackage) {
		if (!SourceVersion.isName(basePackage)) {
			throw new IllegalArgumentException("Not a valid Java package name: " + basePackage);
		}
		this.basePackage = basePackage;
		this.springBootVersion = Objects.requireNonNull(SpringBootVersion.getVersion(),
				"Spring Boot's version is unknown, so generated projects can't be pinned to it");
	}

	/**
	 * Generates {@code project} from the named template into {@code targetDirectory},
	 * which must be missing or empty.
	 * @throws ProjectGenerationException if the inputs or template are invalid, the
	 * target directory isn't empty, or writing fails
	 */
	public void generate(String templateName, NewProject project, Path targetDirectory) {
		validate(project);
		Map<String, String> variables = variables(project);
		List<TemplateFile> rendered = load(templateName).stream().map(file -> render(file, variables)).toList();
		requireEmpty(targetDirectory);
		write(rendered, targetDirectory);
	}

	private void validate(NewProject project) {
		if (project.serviceName() == null || !IDENTIFIER.matcher(project.serviceName()).matches()) {
			throw new ProjectGenerationException("Invalid service name: " + project.serviceName());
		}
		if (project.owner() == null || !IDENTIFIER.matcher(project.owner()).matches()) {
			throw new ProjectGenerationException("Invalid owner: " + project.owner());
		}
		if (project.description() != null
				&& project.description().length() > CatalogConstraints.MAX_DESCRIPTION_LENGTH) {
			throw new ProjectGenerationException(
					"Description is longer than " + CatalogConstraints.MAX_DESCRIPTION_LENGTH + " characters");
		}
	}

	private Map<String, String> variables(NewProject project) {
		ProjectNames names = ProjectNames.of(project.serviceName(), basePackage);
		return Map.of("serviceName", project.serviceName(), "owner", project.owner(), "description",
				(project.description() != null) ? project.description() : NO_DESCRIPTION, "basePackage", basePackage,
				"packageName", names.packageName(), "packagePath", names.packagePath(), "className",
				names.className(), "databaseName", names.databaseName(), "springBootVersion", springBootVersion);
	}

	/** The template's files, sorted by path so that generation is repeatable. */
	private List<TemplateFile> load(String templateName) {
		if (!TEMPLATE_NAME.matcher(templateName).matches()) {
			throw new ProjectGenerationException("Unknown template: " + templateName);
		}
		String root = TEMPLATES_ROOT + templateName + "/";
		Map<String, TemplateFile> files = new TreeMap<>();
		try {
			for (Resource resource : resolver.getResources("classpath*:" + root + "**")) {
				String url = resource.getURL().toString();
				String path = UriUtils.decode(url.substring(url.lastIndexOf(root) + root.length()),
						StandardCharsets.UTF_8);
				boolean directory = path.isEmpty() || path.endsWith("/")
						|| (resource.isFile() && resource.getFile().isDirectory());
				if (!directory) {
					files.putIfAbsent(path, new TemplateFile(path, resource.getContentAsByteArray()));
				}
			}
		}
		catch (IOException ex) {
			throw new ProjectGenerationException("Could not read template " + templateName, ex);
		}
		if (files.isEmpty()) {
			throw new ProjectGenerationException("Unknown template: " + templateName);
		}
		return List.copyOf(files.values());
	}

	private static TemplateFile render(TemplateFile file, Map<String, String> variables) {
		String path = fill(file.path(), variables, file.path());
		String fileName = path.substring(path.lastIndexOf('/') + 1);
		if (fileName.startsWith("_") && !fileName.startsWith("__")) {
			path = path.substring(0, path.length() - fileName.length()) + "." + fileName.substring(1);
		}
		if (path.endsWith(".jar")) {
			return new TemplateFile(path, file.content());
		}
		String content = new String(file.content(), StandardCharsets.UTF_8);
		return new TemplateFile(path, fill(content, variables, file.path()).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Replaces each placeholder in {@code text}. Fails on an unknown variable, and on
	 * a {@code {{} outside a well-formed placeholder, so a typo in a template can
	 * never reach a generated project.
	 */
	private static String fill(String text, Map<String, String> variables, String templatePath) {
		if (PLACEHOLDER.matcher(text).replaceAll("").contains("{{")) {
			throw new ProjectGenerationException("Malformed placeholder in template file " + templatePath);
		}
		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String value = variables.get(matcher.group(1));
			if (value == null) {
				throw new ProjectGenerationException(
						"Unknown placeholder " + matcher.group() + " in template file " + templatePath);
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(value));
		}
		return matcher.appendTail(result).toString();
	}

	private static void requireEmpty(Path directory) {
		if (!Files.exists(directory)) {
			return;
		}
		if (!Files.isDirectory(directory)) {
			throw new ProjectGenerationException(directory + " is a file, not a directory");
		}
		try (Stream<Path> entries = Files.list(directory)) {
			if (entries.findAny().isPresent()) {
				throw new ProjectGenerationException(directory + " is not empty");
			}
		}
		catch (IOException ex) {
			throw new ProjectGenerationException("Could not read " + directory, ex);
		}
	}

	private static void write(List<TemplateFile> files, Path targetDirectory) {
		try {
			for (TemplateFile file : files) {
				Path target = targetDirectory.resolve(file.path()).normalize();
				if (!target.startsWith(targetDirectory.normalize())) {
					throw new ProjectGenerationException("Template file escapes the target directory: " + file.path());
				}
				Files.createDirectories(target.getParent());
				Files.write(target, file.content());
				if (target.getFileName().toString().equals("gradlew")) {
					makeExecutable(target);
				}
			}
		}
		catch (IOException ex) {
			throw new ProjectGenerationException("Could not write the project to " + targetDirectory, ex);
		}
	}

	private static void makeExecutable(Path file) throws IOException {
		try {
			Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(file));
			permissions.addAll(EnumSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE,
					PosixFilePermission.OTHERS_EXECUTE));
			Files.setPosixFilePermissions(file, permissions);
		}
		catch (UnsupportedOperationException ex) {
			// Not a POSIX file system (Windows): best effort.
			file.toFile().setExecutable(true, false);
		}
	}

	private record TemplateFile(String path, byte[] content) {
	}

}
