package brito.com.multitenancy001.controlplane.scheduling.app;

import brito.com.multitenancy001.controlplane.scheduling.domain.AccountJobSchedule;
import brito.com.multitenancy001.controlplane.scheduling.persistence.AccountJobScheduleRepository;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;

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
        var zonedNow = now.atZone(zoneId);

        LocalDate date = zonedNow.toLocalDate();
        var candidateToday = safeZoned(date, localTime, zoneId);

        if (candidateToday.isAfter(zonedNow)) {
            return candidateToday.toInstant();
        }

        var candidateTomorrow = safeZoned(date.plusDays(1), localTime, zoneId);
        return candidateTomorrow.toInstant();
    }

    /**
     * Resolve DST (gap/overlap) de forma determinística:
     * - gap (horário inexistente): ajusta para o primeiro horário válido após a transição.
     * - overlap (horário duplicado): escolhe sempre o offset "mais cedo" (primeiro da lista).
     */
    private java.time.ZonedDateTime safeZoned(LocalDate date, LocalTime time, ZoneId zoneId) {
        LocalDateTime ldt = LocalDateTime.of(date, time);

        ZoneRules rules = zoneId.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(ldt);

        // Normal: 1 offset válido
        if (offsets.size() == 1) {
            return java.time.ZonedDateTime.ofLocal(ldt, zoneId, offsets.get(0));
        }

        // Overlap: 2 offsets válidos (horário duplicado)
        if (offsets.size() == 2) {
            // escolha determinística: offset "mais cedo"
            return java.time.ZonedDateTime.ofLocal(ldt, zoneId, offsets.get(0));
        }

        // Gap: 0 offsets válidos (horário inexistente)
        ZoneOffsetTransition transition = rules.getTransition(ldt);
        if (transition != null) {
            return transition.getDateTimeAfter().atZone(zoneId);
        }

        // Fallback extremo: se não houver transição (muito raro), joga +1h
        return ldt.plusHours(1).atZone(zoneId);
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
