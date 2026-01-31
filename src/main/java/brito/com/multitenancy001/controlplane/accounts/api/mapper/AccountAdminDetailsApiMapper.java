package brito.com.multitenancy001.controlplane.accounts.api.mapper;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.users.api.mapper.ControlPlaneUserApiMapper;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.shared.time.AppClock;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        LocalDateTime now = appClock.now();
        LocalDate today = now.toLocalDate();
        LocalDate end = account.getTrialEndDate() != null ? account.getTrialEndDate().toLocalDate() : null;

        boolean inTrial = account.getTrialEndDate() != null && now.isBefore(account.getTrialEndDate());
        boolean trialExpired = account.getTrialEndDate() != null && now.isAfter(account.getTrialEndDate());

        long trialDaysRemaining = 0;
        if (inTrial && end != null) {
            trialDaysRemaining = ChronoUnit.DAYS.between(today, end);
        }

        return new AccountAdminDetailsResponse(
                // Identificação
                account.getId(),
                account.getDisplayName(),
                account.getSlug(),
                account.getSchemaName(),
                account.getStatus(),
                account.getType(),               // ✅ NOVO (AccountType)
                account.getSubscriptionPlan(),    // ✅ NOVO (SubscriptionPlan)

                // Dados legais
                account.getTaxIdType(),
                account.getTaxIdNumber(),

                // Datas
                account.getCreatedAt(),
                account.getTrialEndDate(),
                account.getPaymentDueDate(),
                account.getDeletedAt(),

                // Flags calculadas
                inTrial,
                trialExpired,
                trialDaysRemaining,

                // Admin
                admin != null ? controlPlaneUserApiMapper.toAdminSummary(admin) : null,

                // Indicadores
                totalUsers,
                account.isOperational(now) // ✅ clock-aware
        );
    }
}
