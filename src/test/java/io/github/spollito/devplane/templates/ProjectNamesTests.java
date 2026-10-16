package io.github.spollito.devplane.templates;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Scenarios from the service-templates spec's "Generated names follow the service name" requirement. */
class ProjectNamesTests {

	private static final String BASE_PACKAGE = "io.github.spollito";

	@Test
	void derivesNamesFromAHyphenatedName() {
		assertThat(ProjectNames.of("orders-api", BASE_PACKAGE)).isEqualTo(new ProjectNames(
				"io.github.spollito.ordersapi", "io/github/spollito/ordersapi", "OrdersApi", "orders_api"));
	}

	@ParameterizedTest
	@CsvSource({ "orders-api-2, io.github.spollito.ordersapi2, OrdersApi2, orders_api_2",
			"api-2b, io.github.spollito.api2b, Api2b, api_2b", "my--service, io.github.spollito.myservice, MyService, my__service" })
	void keepsDigitsInNames(String serviceName, String packageName, String className, String databaseName) {
		ProjectNames names = ProjectNames.of(serviceName, BASE_PACKAGE);

		assertThat(names.packageName()).isEqualTo(packageName);
		assertThat(names.className()).isEqualTo(className);
		assertThat(names.databaseName()).isEqualTo(databaseName);
	}

	@ParameterizedTest
	@CsvSource({ "switch, io.github.spollito.switchservice", "true, io.github.spollito.trueservice",
			"new, io.github.spollito.newservice", "re-cord, io.github.spollito.record" })
	void avoidsJavaKeywordsInThePackage(String serviceName, String packageName) {
		assertThat(ProjectNames.of(serviceName, BASE_PACKAGE).packageName()).isEqualTo(packageName);
	}

}
