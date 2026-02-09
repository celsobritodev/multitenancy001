package brito.com.multitenancy001.controlplane.scheduling.app;

import brito.com.multitenancy001.controlplane.scheduling.domain.AccountJobSchedule;
import brito.com.multitenancy001.controlplane.scheduling.persistence.AccountJobScheduleRepository;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;

@Service
@RequiredArgsConstructor
public class AccountJobScheduleService {

    private final AccountJobScheduleRepository accountJobScheduleRepository;
    private final AppClock appClock;

    /**
     * Calcula o próximo run baseado em "horário civil do tenant".
     * Regra: guarda LocalTime + ZoneId, e converte para Instant só na execução.
     */
    public Instant computeNextRun(Instant now, LocalTime localTime, ZoneId zoneId) {
        ZonedDateTime zonedNow = now.atZone(zoneId);

        LocalDate date = zonedNow.toLocalDate();
        ZonedDateTime candidateToday = safeZoned(date, localTime, zoneId);

        if (candidateToday.isAfter(zonedNow)) {
            return candidateToday.toInstant();
        }

        ZonedDateTime candidateTomorrow = safeZoned(date.plusDays(1), localTime, zoneId);
        return candidateTomorrow.toInstant();
    }

    /**
     * Resolve problemas de DST (gap/overlap) sem explodir:
     * - Se for "gap" (horário inexistente), anda para o próximo horário válido.
     * - Se for "overlap" (horário duplicado), o Java resolve por padrão.
     */
    private ZonedDateTime safeZoned(LocalDate date, LocalTime time, ZoneId zoneId) {
        LocalDateTime ldt = LocalDateTime.of(date, time);
        try {
            return ZonedDateTime.of(ldt, zoneId);
        } catch (DateTimeException ex) {
            // gap: pega o próximo instante válido após a transição
            ZoneRules rules = zoneId.getRules();
            ZoneOffsetTransition t = rules.nextTransition(ldt.atZone(zoneId).toInstant());
            if (t != null) {
                return t.getDateTimeAfter().atZone(zoneId);
            }
            // fallback extremo: joga +1h
            return ZonedDateTime.of(ldt.plusHours(1), zoneId);
        }
    }

    public AccountJobSchedule ensureNextRun(AccountJobSchedule s) {
        Instant now = appClock.instant();
        if (s.getNextRunAt() == null) {
            Instant next = computeNextRun(now, s.getLocalTime(), ZoneId.of(s.getZoneId()));
            s.setNextRunAt(next);
        }
        s.setUpdatedAt(now);
        return accountJobScheduleRepository.save(s);
    }
}
