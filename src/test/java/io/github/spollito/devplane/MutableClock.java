package io.github.spollito.devplane;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock that tests set and advance. Integration tests share one instance, so a
 * test that depends on the time sets it first.
 */
public final class MutableClock extends Clock {

	private volatile Instant instant;

	public MutableClock(Instant instant) {
		this.instant = instant;
	}

	public void setInstant(Instant instant) {
		this.instant = instant;
	}

	public void advance(Duration duration) {
		this.instant = this.instant.plus(duration);
	}

	@Override
	public Instant instant() {
		return this.instant;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("MutableClock is always UTC");
	}

}
