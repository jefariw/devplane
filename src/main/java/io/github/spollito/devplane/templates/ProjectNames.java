package io.github.spollito.devplane.templates;

import java.util.Arrays;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;

/**
 * Every name a generated project takes from its service name. For {@code orders-api}
 * under {@code io.github.spollito}: package {@code io.github.spollito.ordersapi},
 * class name {@code OrdersApi}, and database {@code orders_api}.
 *
 * @param packageName the Java package, the base package plus the service name
 * without hyphens
 * @param packagePath the package as a directory path
 * @param className the service name in PascalCase, used as a class name prefix
 * @param databaseName the service name with hyphens replaced by underscores
 */
record ProjectNames(String packageName, String packagePath, String className, String databaseName) {

	static ProjectNames of(String serviceName, String basePackage) {
		String segment = serviceName.replace("-", "");
		// A segment like "switch" or "true" isn't a legal package name.
		if (SourceVersion.isKeyword(segment)) {
			segment += "service";
		}
		String packageName = basePackage + "." + segment;
		String className = Arrays.stream(serviceName.split("-"))
			.filter(part -> !part.isEmpty()) // "my--service" is a valid name
			.map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
			.collect(Collectors.joining());
		return new ProjectNames(packageName, packageName.replace('.', '/'), className,
				serviceName.replace('-', '_'));
	}

}
