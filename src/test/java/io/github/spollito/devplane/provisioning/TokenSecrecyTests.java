package io.github.spollito.devplane.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import io.github.spollito.devplane.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The repository-provisioning spec's "The token stays secret" requirement. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class TokenSecrecyTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private FakeGitHub gitHub;

	@TempDir
	private Path workspace;

	@BeforeEach
	void registerServices() {
		gitHub.reset();
		for (String name : new String[] { "orders-api", "payments-api" }) {
			mvc.delete().uri("/api/v1/services/" + name).exchange();
			assertThat(mvc.post().uri("/api/v1/services").contentType(MediaType.APPLICATION_JSON).content("""
					{"name": "%s", "owner": "payments-team"}
					""".formatted(name))).hasStatus(HttpStatus.CREATED);
		}
	}

	@Test
	void theTokenAppearsNowhere(CapturedOutput output) throws Exception {
		MvcTestResult provisioned = mvc.post().uri("/api/v1/services/orders-api/repository").exchange();
		gitHub.respondWith(401, """
				{"message": "Bad credentials", "status": "401"}
				""");
		MvcTestResult rejected = mvc.post().uri("/api/v1/services/payments-api/repository").exchange();

		assertThat(provisioned).hasStatus(HttpStatus.CREATED);
		assertThat(rejected).hasStatus(HttpStatus.BAD_GATEWAY);
		for (MvcTestResult result : new MvcTestResult[] { provisioned, rejected }) {
			assertThat(result.getResponse().getContentAsString()).doesNotContain(FakeGitHub.TOKEN);
			assertThat(result.getResponse().getHeaderNames())
				.allSatisfy(name -> assertThat(result.getResponse().getHeaders(name)).noneMatch(
						value -> value.contains(FakeGitHub.TOKEN)));
		}
		assertThat(output).contains("Provisioned https://github.com/octo-dev/orders-api", "GitHub answered 401")
			.doesNotContain(FakeGitHub.TOKEN);
		ClonedRepository clone = ClonedRepository.of(gitHub.repository("orders-api").orElseThrow().directory(),
				workspace.resolve("clone"));
		assertThat(clone.commits()).singleElement().satisfies(commit -> {
			assertThat(commit.getFullMessage()).doesNotContain(FakeGitHub.TOKEN);
			assertThat(commit.getAuthorIdent().toExternalString()).doesNotContain(FakeGitHub.TOKEN);
			assertThat(commit.getCommitterIdent().toExternalString()).doesNotContain(FakeGitHub.TOKEN);
		});
		try (Stream<Path> files = Files.walk(clone.directory())) {
			Path gitDirectory = clone.directory().resolve(".git");
			assertThat(files.filter(Files::isRegularFile).filter(file -> !file.startsWith(gitDirectory)))
				.isNotEmpty()
				.allSatisfy(file -> assertThat(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1))
					.as(file.toString())
					.doesNotContain(FakeGitHub.TOKEN));
		}
	}

}
