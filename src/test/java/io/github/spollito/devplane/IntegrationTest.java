package io.github.spollito.devplane;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * The full application with MockMvc, a Testcontainers PostgreSQL, a
 * {@link MutableClock} in place of the system clock, and a fake GitHub.
 * <p>
 * Every integration test uses exactly this configuration, so they all share one
 * cached Spring context and one database container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, FakeGitHubConfiguration.class })
public @interface IntegrationTest {

}
