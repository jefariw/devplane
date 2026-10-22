package io.github.spollito.devplane.templates;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads the zip archives that projects are downloaded as. */
final class ProjectArchives {

	private ProjectArchives() {
	}

	/** The archive's files (not its directories), by path, each with a hash of its content. */
	static Map<String, String> files(byte[] archive) throws IOException {
		Map<String, String> files = new TreeMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if (!entry.isDirectory()) {
					files.put(entry.getName(), hash(zip.readAllBytes()));
				}
			}
		}
		return files;
	}

	/**
	 * The files under {@code directory}, by {@code prefix} plus their path relative to
	 * it, each with a hash of its content, for comparison with {@link #files(byte[])}.
	 */
	static Map<String, String> files(Path directory, String prefix) throws IOException {
		Map<String, String> files = new TreeMap<>();
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path file : paths.filter(Files::isRegularFile).toList()) {
				String path = String.join("/", directory.relativize(file).toString().split("[/\\\\]"));
				files.put(prefix + path, hash(Files.readAllBytes(file)));
			}
		}
		return files;
	}

	/** The content of one of the archive's files, as text. */
	static String content(byte[] archive, String path) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if (entry.getName().equals(path)) {
					return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
		}
		throw new AssertionError("The archive has no file " + path);
	}

	/**
	 * The Unix mode of every entry, such as {@code rwxr-xr-x}, by path. An entry
	 * without a mode, which unzip tools give their default permissions, reads as
	 * {@code ---------}.
	 */
	static Map<String, String> unixModes(byte[] archive, Path workDirectory) throws IOException {
		Path file = Files.write(Files.createTempFile(workDirectory, "archive-", ".zip"), archive);
		Map<String, String> modes = new TreeMap<>();
		try (FileSystem zip = FileSystems.newFileSystem(file,
				Map.of("enablePosixFileAttributes", "true", "defaultPermissions", "---------"));
				Stream<Path> paths = Files.walk(zip.getPath("/"))) {
			for (Path path : paths.filter(path -> path.getNameCount() > 0).toList()) {
				modes.put(path.toString().substring(1),
						PosixFilePermissions.toString(Files.getPosixFilePermissions(path)));
			}
		}
		return modes;
	}

	private static String hash(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
