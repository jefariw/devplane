package io.github.spollito.devplane.provisioning;

import java.io.IOException;
import java.nio.file.Path;

import io.github.spollito.devplane.provisioning.GitHubClient.CreatedRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Commits a generated project and pushes it to a new repository, as the single
 * first commit on {@code main}.
 * <p>
 * The developer's own Git configuration must not change what's pushed, so the
 * repository turns off line-ending conversion and points {@code core.hooksPath} at
 * its own empty hooks folder, and the commit is never signed.
 */
@Component
class ProjectPusher {

	private static final Logger log = LoggerFactory.getLogger(ProjectPusher.class);

	private static final String BRANCH = "main";

	private static final int PUSH_TIMEOUT_SECONDS = 60;

	/** GitHub's documented user name for authenticating with a token over HTTPS. */
	private static final String TOKEN_USER = "x-access-token";

	/** The generator makes this file executable; Git has to record that explicitly. */
	private static final String GRADLE_WRAPPER = "gradlew";

	private final GitHubProperties properties;

	ProjectPusher(GitHubProperties properties) {
		this.properties = properties;
	}

	/**
	 * Commits every file in {@code projectDirectory} and pushes the commit to
	 * {@code repository}. Creates a {@code .git} folder in the directory.
	 * @throws GitHubException if anything fails, naming the repository that was left
	 * empty
	 */
	void push(Path projectDirectory, CreatedRepository repository, String message) {
		PersonIdent owner = new PersonIdent(repository.owner().login(), noReplyEmail(repository.owner()));
		try (Git git = Git.init().setDirectory(projectDirectory.toFile()).setInitialBranch(BRANCH).call()) {
			isolateFromUserConfiguration(git.getRepository());
			git.add().addFilepattern(".").call();
			makeExecutable(git.getRepository(), GRADLE_WRAPPER);
			git.commit()
				.setMessage(message)
				.setAuthor(owner)
				.setCommitter(owner)
				.setSign(false)
				.setNoVerify(true)
				.call();
			Iterable<PushResult> results = git.push()
				.setRemote(repository.cloneUrl().toString())
				.setRefSpecs(new RefSpec("refs/heads/" + BRANCH + ":refs/heads/" + BRANCH))
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(TOKEN_USER, token()))
				.setTimeout(PUSH_TIMEOUT_SECONDS)
				.call();
			requireAccepted(results);
		}
		catch (GitAPIException | IOException | IllegalStateException ex) {
			log.warn("Could not push the project to {}", repository.htmlUrl(), ex);
			throw new GitHubException("GitHub created " + repository.htmlUrl()
					+ ", but the project could not be pushed to it. Delete that empty repository on GitHub before"
					+ " provisioning again.", ex);
		}
	}

	/**
	 * GitHub links commits from this address to the account, without DevPlane
	 * needing permission to read the account's email address.
	 */
	private static String noReplyEmail(CreatedRepository.Owner owner) {
		return owner.id() + "+" + owner.login() + "@users.noreply.github.com";
	}

	private static void isolateFromUserConfiguration(Repository repository) throws IOException {
		StoredConfig config = repository.getConfig();
		config.setBoolean("core", null, "autocrlf", false);
		config.setString("core", null, "hooksPath", repository.getDirectory().toPath().resolve("hooks").toString());
		config.save();
	}

	/** Sets the staged mode directly, so it's right even where files have no executable bit. */
	private static void makeExecutable(Repository repository, String path) throws IOException {
		DirCache index = repository.lockDirCache();
		try {
			DirCacheEntry entry = index.getEntry(path);
			if (entry != null) {
				entry.setFileMode(FileMode.EXECUTABLE_FILE);
				index.write();
				index.commit();
			}
		}
		finally {
			index.unlock();
		}
	}

	/** JGit reports a rejected update in the result instead of throwing. */
	private static void requireAccepted(Iterable<PushResult> results) {
		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				if (update.getStatus() != RemoteRefUpdate.Status.OK
						&& update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
					throw new IllegalStateException(
							"Push of " + update.getRemoteName() + " was rejected: " + update.getStatus());
				}
			}
		}
	}

	private String token() {
		String token = properties.token();
		if (!properties.hasToken() || token == null) {
			throw new IllegalStateException("No GitHub token is configured");
		}
		return token;
	}

}
