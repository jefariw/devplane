package io.github.spollito.devplane.provisioning;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;

/**
 * Calls the one GitHub REST API endpoint DevPlane needs: creating a repository in
 * the account that owns the token.
 */
@Component
class GitHubClient {

	private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

	private final GitHubProperties properties;

	private final RestClient restClient;

	GitHubClient(GitHubProperties properties) {
		this.properties = properties;
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
		requestFactory.setReadTimeout(READ_TIMEOUT);
		this.restClient = RestClient.builder()
			.baseUrl(properties.apiUrl().toString())
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
			.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
			.build();
	}

	/**
	 * Creates an empty repository.
	 * @throws RepositoryNameTakenException if the account already has a repository
	 * with this name
	 * @throws GitHubException if GitHub rejects the token, fails, or can't be reached
	 */
	CreatedRepository createRepository(String name, @Nullable String description, boolean isPrivate) {
		String token = properties.token();
		if (!properties.hasToken() || token == null) {
			throw new IllegalStateException("No GitHub token is configured");
		}
		try {
			return restClient.post()
				.uri("/user/repos")
				.headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.body(new CreateRepositoryRequest(name, description, isPrivate, false))
				.exchange((request, response) -> handle(name, response));
		}
		catch (RestClientException ex) {
			log.warn("Could not reach GitHub at {} to create repository {}", properties.apiUrl(), name, ex);
			throw new GitHubException("Could not reach GitHub to create the repository.", ex);
		}
		catch (IllegalArgumentException ex) {
			// The HTTP client rejects a header value with characters such as a line break,
			// and its message quotes the value, token included, so the exception is
			// neither logged nor kept as the cause.
			log.warn("Could not send the GitHub token: it contains characters that aren't allowed in an HTTP header");
			throw new GitHubException("The GitHub token contains characters that can't be sent to GitHub."
					+ " Check DEVPLANE_GITHUB_TOKEN.", null);
		}
	}

	private CreatedRepository handle(String name, ConvertibleClientHttpResponse response) throws IOException {
		int status = response.getStatusCode().value();
		if (status == HttpStatus.CREATED.value()) {
			CreatedRepository created = response.bodyTo(CreatedRepository.class);
			if (created == null) {
				throw new GitHubException("GitHub's response did not describe the new repository.", null);
			}
			return created;
		}
		String requestId = response.getHeaders().getFirst("X-GitHub-Request-Id");
		log.warn("GitHub answered {} when creating repository {} (request id {})", status, name, requestId);
		if (status == HttpStatus.UNPROCESSABLE_CONTENT.value() && nameTaken(response)) {
			throw new RepositoryNameTakenException(name);
		}
		if (status == HttpStatus.UNAUTHORIZED.value()) {
			throw new GitHubException(
					"GitHub rejected the token. Check that DEVPLANE_GITHUB_TOKEN is a valid, unexpired token.", null);
		}
		throw new GitHubException("GitHub could not create the repository (status " + status + ").", null);
	}

	private static boolean nameTaken(ConvertibleClientHttpResponse response) {
		try {
			GitHubError error = response.bodyTo(GitHubError.class);
			return error != null && error.errors() != null && error.errors()
				.stream()
				.anyMatch(field -> "name".equals(field.field()) && field.message() != null
						&& field.message().contains("already exists"));
		}
		catch (RuntimeException ex) {
			// Not the error body GitHub documents, so not a name conflict.
			return false;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record CreateRepositoryRequest(String name, @Nullable String description,
			@JsonProperty("private") boolean isPrivate, @JsonProperty("auto_init") boolean autoInit) {

	}

	/**
	 * The parts of GitHub's response that DevPlane uses.
	 *
	 * @param htmlUrl the repository's page, which the catalog records
	 * @param cloneUrl where to push
	 * @param owner the account that owns the token
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record CreatedRepository(@JsonProperty("html_url") URI htmlUrl, @JsonProperty("clone_url") URI cloneUrl,
			Owner owner) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Owner(String login, long id) {

		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GitHubError(@Nullable String message, @Nullable List<FieldError> errors) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		record FieldError(@Nullable String field, @Nullable String message) {

		}

	}

}
