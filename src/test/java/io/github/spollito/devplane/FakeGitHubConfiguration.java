package io.github.spollito.devplane;

import io.github.spollito.devplane.provisioning.FakeGitHub;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points DevPlane at a {@link FakeGitHub} with its test token, so integration tests
 * never reach the real GitHub, even when {@code DEVPLANE_GITHUB_TOKEN} is set.
 */
@TestConfiguration(proxyBeanMethods = false)
class FakeGitHubConfiguration {

	@Bean(destroyMethod = "close")
	FakeGitHub fakeGitHub() {
		return FakeGitHub.start();
	}

	@Bean
	DynamicPropertyRegistrar fakeGitHubProperties(FakeGitHub gitHub) {
		return registry -> {
			registry.add("devplane.github.api-url", gitHub::url);
			registry.add("devplane.github.token", () -> FakeGitHub.TOKEN);
		};
	}

}
