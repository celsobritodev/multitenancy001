package brito.com.multitenancy001.controlplane.scheduling.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.scheduling.domain.AccountJobSchedule;
import brito.com.multitenancy001.controlplane.scheduling.persistence.AccountJobScheduleRepository;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de aplicação responsável por calcular e garantir o próximo instante
 * de execução dos agendamentos de conta.
 *
 * <p>Regras adotadas:</p>
 * <ul>
 *   <li>O agendamento é definido por horário civil do tenant
 *       ({@link LocalTime} + {@link ZoneId}).</li>
 *   <li>O instante efetivo de execução persistido é sempre um {@link Instant}.</li>
 *   <li>A resolução de gap/overlap de DST é determinística.</li>
 *   <li>{@link AppClock} é a única fonte de tempo do serviço.</li>
 * </ul>
 *
 * <p>Política de resolução de horário civil:</p>
 * <ul>
 *   <li>Horário válido: usa o único offset disponível.</li>
 *   <li>Overlap (horário duplicado): usa o primeiro offset válido,
 *       de forma determinística.</li>
 *   <li>Gap (horário inexistente): ajusta para o primeiro horário válido
 *       após a transição.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountJobScheduleService {

    private final AccountJobScheduleRepository accountJobScheduleRepository;
    private final AppClock appClock;

    /**
     * Calcula o próximo instante de execução com base no horário civil do tenant.
     *
     * <p>Se o horário configurado ainda não ocorreu no dia corrente da timezone
     * informada, retorna o instante correspondente ao dia atual. Caso contrário,
     * retorna o instante correspondente ao dia seguinte.</p>
     *
     * @param now instante atual de referência
     * @param localTime horário civil configurado
     * @param zoneId timezone do tenant
     * @return próximo instante de execução
     */
    public Instant computeNextRun(Instant now, LocalTime localTime, ZoneId zoneId) {
        var zonedNow = now.atZone(zoneId);
        LocalDate currentDate = zonedNow.toLocalDate();

        Instant candidateToday = resolveScheduledInstant(currentDate, localTime, zoneId);
        if (candidateToday.isAfter(now)) {
            log.debug(
                    "Próximo run calculado para hoje | now={} | zoneId={} | localTime={} | nextRunAt={}",
                    now,
                    zoneId,
                    localTime,
                    candidateToday
            );
            return candidateToday;
        }

        Instant candidateTomorrow = resolveScheduledInstant(currentDate.plusDays(1), localTime, zoneId);
        log.debug(
                "Próximo run calculado para amanhã | now={} | zoneId={} | localTime={} | nextRunAt={}",
                now,
                zoneId,
                localTime,
                candidateTomorrow
        );
        return candidateTomorrow;
    }

    /**
     * Garante que o agendamento possua próximo instante de execução definido e
     * atualiza o timestamp de alteração antes de persistir.
     *
     * @param accountJobSchedule agendamento a ser validado/persistido
     * @return agendamento persistido
     */
    public AccountJobSchedule ensureNextRun(AccountJobSchedule accountJobSchedule) {
        Instant now = appClock.instant();

        if (accountJobSchedule.getNextRunAt() == null) {
            Instant nextRunAt = computeNextRun(
                    now,
                    accountJobSchedule.getLocalTime(),
                    ZoneId.of(accountJobSchedule.getZoneId())
            );
            accountJobSchedule.setNextRunAt(nextRunAt);

            log.info(
                    "Next run inicial definido para agendamento | scheduleId={} | zoneId={} | localTime={} | nextRunAt={}",
                    accountJobSchedule.getId(),
                    accountJobSchedule.getZoneId(),
                    accountJobSchedule.getLocalTime(),
                    nextRunAt
            );
        }

        accountJobSchedule.setUpdatedAt(now);
        AccountJobSchedule savedAccountJobSchedule = accountJobScheduleRepository.save(accountJobSchedule);

        log.debug(
                "Agendamento persistido com sucesso | scheduleId={} | updatedAt={} | nextRunAt={}",
                savedAccountJobSchedule.getId(),
                savedAccountJobSchedule.getUpdatedAt(),
                savedAccountJobSchedule.getNextRunAt()
        );

        return savedAccountJobSchedule;
    }

    /**
     * Resolve um horário civil em um instante absoluto de forma determinística,
     * respeitando as regras de timezone e DST.
     *
     * <p>Estratégia:</p>
     * <ul>
     *   <li>Se houver 1 offset válido, usa esse offset.</li>
     *   <li>Se houver 2 offsets válidos (overlap), usa o primeiro.</li>
     *   <li>Se não houver offset válido (gap), usa o primeiro horário válido
     *       após a transição.</li>
     * </ul>
     *
     * @param date data civil do tenant
     * @param time horário civil do tenant
     * @param zoneId timezone do tenant
     * @return instante absoluto correspondente
     */
    private Instant resolveScheduledInstant(LocalDate date, LocalTime time, ZoneId zoneId) {
        ZoneRules zoneRules = zoneId.getRules();
        List<ZoneOffset> validOffsets = zoneRules.getValidOffsets(date.atTime(time));

        if (validOffsets.size() == 1) {
            ZoneOffset selectedOffset = validOffsets.get(0);
            return date.atTime(time).toInstant(selectedOffset);
        }

        if (validOffsets.size() == 2) {
            ZoneOffset selectedOffset = validOffsets.get(0);
            log.warn(
                    "Overlap de DST detectado; aplicando primeiro offset válido | date={} | time={} | zoneId={} | offset={}",
                    date,
                    time,
                    zoneId,
                    selectedOffset
            );
            return date.atTime(time).toInstant(selectedOffset);
        }

        ZoneOffsetTransition transition = zoneRules.getTransition(date.atTime(time));
        if (transition != null) {
            Instant adjustedInstant = transition.getInstant();
            log.warn(
                    "Gap de DST detectado; ajustando para o primeiro horário válido após a transição | date={} | time={} | zoneId={} | adjustedInstant={}",
                    date,
                    time,
                    zoneId,
                    adjustedInstant
            );
            return adjustedInstant;
        }

        Instant fallbackInstant = date.plusDays(1).atStartOfDay(zoneId).toInstant();
        log.error(
                "Nenhum offset/transição resolvido; aplicando fallback defensivo | date={} | time={} | zoneId={} | fallbackInstant={}",
                date,
                time,
                zoneId,
                fallbackInstant
        );
        return fallbackInstant;
    }
}