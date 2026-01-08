package brito.com.multitenancy001.controlplane.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infra.multitenancy.TenantSchemaContext;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.application.provisioning.TenantSchemaProvisioningService;
import brito.com.multitenancy001.tenant.model.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountTenantUserService {

	private final AccountRepository accountRepository;
	private final TenantSchemaProvisioningService tenantSchemaService;
	private final TenantUserRepository tenantUserRepository;

	public List<TenantUserSummaryResponse> listTenantUsers(Long accountId, boolean onlyActive) {
		// resolve conta em PUBLIC
		TenantSchemaContext.clearTenantSchema();
		Account account = accountRepository.findByIdAndDeletedFalse(accountId)
				.orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

		String schema = account.getSchemaName();

		if (!tenantSchemaService.validateTenantSchema(schema)) {
			throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "Schema do tenant não existe", 404);
		}
		if (!tenantSchemaService.tableExists(schema, "users_tenant")) {
			throw new ApiException("TENANT_TABLE_NOT_FOUND", "Tabela users_tenant não existe no tenant", 404);
		}

		TenantSchemaContext.bindTenantSchema(schema);
		try {
			return listTenantUsersTx(account.getId(), onlyActive);
		} finally {
			TenantSchemaContext.clearTenantSchema();
		}
	}

	@Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
	protected List<TenantUserSummaryResponse> listTenantUsersTx(Long accountId, boolean onlyActive) {
		List<TenantUser> users;

		if (onlyActive) {
			// Use o novo método customizado
			users = tenantUserRepository.findActiveUsersByAccount(accountId);
		} else {
			users = tenantUserRepository.findByAccountId(accountId);
		}
		return users.stream().map(TenantUserSummaryResponse::from).toList();

	}

	public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
		TenantSchemaContext.clearTenantSchema();

		// resolve schema no PUBLIC
		Account account = accountRepository.findByIdAndDeletedFalse(accountId)
				.orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

		String schema = account.getSchemaName();
		if (!tenantSchemaService.validateTenantSchema(schema)
				|| !tenantSchemaService.tableExists(schema, "users_tenant")) {
			throw new ApiException("TENANT_SCHEMA_NOT_FOUND", "Schema do tenant inválido", 404);
		}

		TenantSchemaContext.bindTenantSchema(schema);
		try {
			setUserSuspendedByAdminTx(accountId, userId, suspended);
		} finally {
			TenantSchemaContext.clearTenantSchema();
		}
	}

	@Transactional(transactionManager = "tenantTransactionManager")
	protected void setUserSuspendedByAdminTx(Long accountId, Long userId, boolean suspended) {
		int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
		if (updated == 0) {
			throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404);
		}
	}
}
