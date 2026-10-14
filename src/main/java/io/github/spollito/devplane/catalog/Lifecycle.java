package io.github.spollito.devplane.catalog;

import java.util.Locale;

/** Where a service is in its lifecycle. The API uses the lowercase names. */
public enum Lifecycle {

	EXPERIMENTAL, PRODUCTION, DEPRECATED;

	/** Matches exactly the values {@link #fromApiValue} accepts. */
	static final String API_VALUE_PATTERN = "experimental|production|deprecated";

	String apiValue() {
		return name().toLowerCase(Locale.ROOT);
	}

	static Lifecycle fromApiValue(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

}
