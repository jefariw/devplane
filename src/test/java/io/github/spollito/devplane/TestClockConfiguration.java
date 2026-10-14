package io.github.spollito.devplane;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the application's system clock with a {@link MutableClock}. */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(Instant.parse("2026-01-15T10:00:00Z"));
	}

}
