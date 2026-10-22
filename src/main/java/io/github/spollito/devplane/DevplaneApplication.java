package io.github.spollito.devplane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DevplaneApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevplaneApplication.class, args);
	}

}
