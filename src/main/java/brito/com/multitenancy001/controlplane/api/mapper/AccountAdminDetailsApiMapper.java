package brito.com.multitenancy001.controlplane.api.mapper;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
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
                account.getId(),
                account.getDisplayName(),
                account.getSlug(),
                account.getSchemaName(),
                account.getStatus().name(),

                account.getTaxIdType(),
                account.getTaxIdNumber(),

                account.getCreatedAt(),
                account.getTrialEndDate(),
                account.getPaymentDueDate(),
                account.getDeletedAt(),

                inTrial,
                trialExpired,
                trialDaysRemaining,

                admin != null ? controlPlaneUserApiMapper.toAdminSummary(admin) : null,

                totalUsers,
                account.isActive(now) // ✅ agora clock-aware
        );
    }
}
