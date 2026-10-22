package io.github.spollito.devplane.provisioning;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * What a test sees when it clones a pushed repository: the commits on {@code main},
 * each committed file's Git mode, and the checked-out files.
 *
 * @param directory the checkout
 * @param commits the commits on {@code main}, newest first
 * @param modes each file in the newest commit, by path, with its Git file mode
 */
record ClonedRepository(Path directory, List<RevCommit> commits, Map<String, FileMode> modes) {

	/**
	 * Clones {@code bareRepository} into {@code directory}. The checkout ignores the
	 * developer's line-ending settings, so files compare equal on every OS.
	 */
	static ClonedRepository of(Path bareRepository, Path directory) {
		try (Git git = Git.cloneRepository()
			.setURI(bareRepository.toUri().toString())
			.setDirectory(directory.toFile())
			.setBranch("main")
			.setNoCheckout(true)
			.call()) {
			Repository repository = git.getRepository();
			StoredConfig config = repository.getConfig();
			config.setBoolean("core", null, "autocrlf", false);
			config.save();
			git.reset().setMode(ResetType.HARD).setRef("origin/main").call();
			List<RevCommit> commits = new ArrayList<>();
			git.log().add(repository.resolve("origin/main")).call().forEach(commits::add);
			Map<String, FileMode> modes = new TreeMap<>();
			try (TreeWalk walk = new TreeWalk(repository)) {
				walk.addTree(commits.getFirst().getTree());
				walk.setRecursive(true);
				while (walk.next()) {
					modes.put(walk.getPathString(), walk.getFileMode(0));
				}
			}
			return new ClonedRepository(directory, List.copyOf(commits), modes);
		}
		catch (GitAPIException ex) {
			throw new IllegalStateException(ex);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** The checked-out files, without {@code .git}, as {@link #files(Path)} reports them. */
	Map<String, String> files() {
		Map<String, String> files = files(directory);
		files.keySet().removeIf(path -> path.startsWith(".git/"));
		return files;
	}

	/** Every file under {@code directory}, by {@code /}-separated relative path, with a hash of its content. */
	static Map<String, String> files(Path directory) {
		Map<String, String> files = new TreeMap<>();
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path file : paths.filter(Files::isRegularFile).toList()) {
				String path = String.join("/", directory.relativize(file).toString().split("[/\\\\]"));
				files.put(path, HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
			}
			return files;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
