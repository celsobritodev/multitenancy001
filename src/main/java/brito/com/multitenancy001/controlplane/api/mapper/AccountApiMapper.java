package brito.com.multitenancy001.controlplane.api.mapper;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneAdminUserSummaryResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccountApiMapper {
	
	private final ControlPlaneUserApiMapper controlPlaneUserApiMapper;
	
	

	public AccountResponse toResponse(Account account) {
	    return new AccountResponse(
	        account.getId(),
	        account.getName(),
	        account.getSlug(),
	        account.getSchemaName(),
	        account.getStatus().name(),
	        account.getType().name(),
	        account.getCreatedAt(),
	        account.getTrialEndDate(),
	        null
	    );
	}


   public AccountResponse toResponse(Account account, ControlPlaneUser adminUser) {
    ControlPlaneAdminUserSummaryResponse adminResponse =
            adminUser != null ? controlPlaneUserApiMapper.toAdminSummary(adminUser) : null;

    return new AccountResponse(
            account.getId(),
            account.getName(),
            account.getSlug(),
            account.getSchemaName(),
            account.getStatus().name(),
            account.getType().name(),
            account.getCreatedAt(),
            account.getTrialEndDate(),
            adminResponse
    );
}

}
