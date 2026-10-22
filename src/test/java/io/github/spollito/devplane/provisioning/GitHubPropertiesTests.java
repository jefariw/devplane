package io.github.spollito.devplane.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class GitHubPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesConfiguration.class);

	@Test
	void bindsTheTokenFromTheEnvironment() {
		contextRunner.withInitializer(context -> context.getEnvironment()
			.getPropertySources()
			.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
					new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
							Map.of("DEVPLANE_GITHUB_TOKEN", "github_pat_example"))))
			.withPropertyValues("devplane.github.api-url=https://api.github.com")
			.run(context -> {
				GitHubProperties properties = context.getBean(GitHubProperties.class);
				assertThat(properties.token()).isEqualTo("github_pat_example");
				assertThat(properties.hasToken()).isTrue();
				assertThat(properties.apiUrl()).isEqualTo(URI.create("https://api.github.com"));
			});
	}

	@Test
	void hasNoTokenUntilOneIsSet() {
		contextRunner.withPropertyValues("devplane.github.api-url=https://api.github.com")
			.run(context -> assertThat(context.getBean(GitHubProperties.class).hasToken()).isFalse());
		contextRunner.withPropertyValues("devplane.github.token= ")
			.run(context -> assertThat(context.getBean(GitHubProperties.class).hasToken()).isFalse());
	}

	@Test
	void leavesTheTokenOutOfToString() {
		GitHubProperties properties = new GitHubProperties("github_pat_example", URI.create("https://api.github.com"));

		assertThat(properties.toString()).doesNotContain("github_pat_example").contains("(set)");
		assertThat(new GitHubProperties(null, URI.create("https://api.github.com"))).hasToString(
				"GitHubProperties[apiUrl=https://api.github.com, token=(not set)]");
	}

	@EnableConfigurationProperties(GitHubProperties.class)
	static class PropertiesConfiguration {

	}

}
