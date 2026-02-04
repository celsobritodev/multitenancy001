package brito.com.multitenancy001.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Regra do projeto:
 * - "Instante real" => Instant (UTC) via AppClock.instant()
 * - "Data civil"    => LocalDate via AppClock.today()
 *
 * Não expor Instant para evitar "meio termo perigoso" que vaza pro domínio.
 */
public interface AppClock {

    Clock clock();

    default Instant instant() {
        return Instant.now(clock());
    }

    default ZoneId zone() {
        return clock().getZone();
    }

    /**
     * Data civil no fuso do clock (normalmente UTC, mas pode ser ZoneId do tenant
     * em cenários específicos de UX).
     */
    default LocalDate today() {
        return LocalDate.now(clock());
    }

    default long epochMillis() {
        return instant().toEpochMilli();
    }
}

