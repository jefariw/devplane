package io.github.spollito.devplane;

import org.springframework.boot.SpringApplication;

public class TestDevplaneApplication {

	public static void main(String[] args) {
		SpringApplication.from(DevplaneApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
