package brito.com.multitenancy001.controlplane.accounts.api.mapper;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.users.api.mapper.ControlPlaneUserApiMapper;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.shared.time.AppClock;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Component
public class AccountAdminDetailsApiMapper {

    private final AppClock appClock;
    private final ControlPlaneUserApiMapper controlPlaneUserApiMapper;

    public AccountAdminDetailsApiMapper(AppClock appClock, ControlPlaneUserApiMapper controlPlaneUserApiMapper) {
        this.appClock = appClock;
        this.controlPlaneUserApiMapper = controlPlaneUserApiMapper;
    }

    public AccountAdminDetailsResponse toResponse(Account account, ControlPlaneUser admin, long totalUsers) {
        Instant now = appClock.instant();

        // Trial é instante real (Instant). Para "dias restantes", convertemos para data civil em UTC explicitamente.
        LocalDate todayUtc = LocalDate.ofInstant(now, ZoneOffset.UTC);

        Instant trialEndAt = account.getTrialEndAt();
        LocalDate trialEndDateUtc = trialEndAt != null ? LocalDate.ofInstant(trialEndAt, ZoneOffset.UTC) : null;

        boolean inTrial = trialEndAt != null && now.isBefore(trialEndAt);
        boolean trialExpired = trialEndAt != null && now.isAfter(trialEndAt);

        long trialDaysRemaining = 0;
        if (inTrial && trialEndDateUtc != null) {
            trialDaysRemaining = ChronoUnit.DAYS.between(todayUtc, trialEndDateUtc);
            if (trialDaysRemaining < 0) trialDaysRemaining = 0;
        }

        return new AccountAdminDetailsResponse(
                // Identificação
                account.getId(),
                account.getDisplayName(),
                account.getSlug(),
                account.getSchemaName(),
                account.getStatus(),
                account.getType(),
                account.getSubscriptionPlan(),

                // Dados legais
                account.getTaxIdType(),
                account.getTaxIdNumber(),

                // Datas (semântica correta)
                account.getAudit() != null ? account.getAudit().getCreatedAt() : null,
                account.getTrialEndAt(),
                account.getPaymentDueDate(),
                account.getAudit() != null ? account.getAudit().getDeletedAt() : null,

                // Flags calculadas
                inTrial,
                trialExpired,
                trialDaysRemaining,

                // Admin
                admin != null ? controlPlaneUserApiMapper.toAdminSummary(admin) : null,

                // Indicadores
                totalUsers,
                account.isOperational() // aqui não precisa de "now" porque seu domínio já tem isOperational()
        );
    }
}
