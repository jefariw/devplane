package {{packageName}};

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class {{className}}ApplicationTests {

	@Test
	void contextLoads() {
	}

}
