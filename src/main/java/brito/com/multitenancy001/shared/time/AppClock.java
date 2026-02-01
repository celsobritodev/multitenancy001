package brito.com.multitenancy001.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public interface AppClock {

    Clock clock();

    default Instant instant() {
        return Instant.now(clock());
    }

    default LocalDateTime now() {
        return LocalDateTime.now(clock());
    }

    default ZoneId zone() {
        return clock().getZone();
    }

    default long epochMillis() {
        return instant().toEpochMilli();
    }
}