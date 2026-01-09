package brito.com.multitenancy001.controlplane.api.mapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;

@Component
public class AccountAdminDetailsApiMapper {
	
	private final Clock clock;
	private final ControlPlaneUserApiMapper controlPlaneUserApiMapper;
	


	public AccountAdminDetailsApiMapper(Clock clock, ControlPlaneUserApiMapper controlPlaneUserApiMapper) {
	    this.clock = clock;
	    this.controlPlaneUserApiMapper = controlPlaneUserApiMapper;
	}


   public AccountAdminDetailsResponse toResponse(Account account, ControlPlaneUser admin, long totalUsers) {
	   LocalDateTime now = LocalDateTime.now(clock);
	   LocalDate today = now.toLocalDate();
	   LocalDate end = account.getTrialEndDate() != null ? account.getTrialEndDate().toLocalDate() : null;  
	   
	   


    boolean inTrial = account.getTrialEndDate() != null && now.isBefore(account.getTrialEndDate());
    boolean trialExpired = account.getTrialEndDate() != null && now.isAfter(account.getTrialEndDate());

    long trialDaysRemaining = 0;
    if (inTrial) {
        trialDaysRemaining = ChronoUnit.DAYS.between(today, end);
    }

    return new AccountAdminDetailsResponse(
        account.getId(),
        account.getName(),
        account.getSlug(),
        account.getSchemaName(),
        account.getStatus().name(),

        account.getCompanyDocType(),
        account.getCompanyDocNumber(),

        account.getCreatedAt(),
        account.getTrialEndDate(),
        account.getPaymentDueDate(),
        account.getDeletedAt(),

        inTrial,
        trialExpired,
        trialDaysRemaining,

        admin != null ? controlPlaneUserApiMapper.toAdminSummary(admin) : null,



        totalUsers,
        account.isActive()
    );
}

}
