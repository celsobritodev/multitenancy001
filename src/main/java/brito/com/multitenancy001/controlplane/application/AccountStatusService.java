package brito.com.multitenancy001.controlplane.application;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infra.multitenancy.TenantSchemaContext;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.application.provisioning.TenantSchemaProvisioningService;
import brito.com.multitenancy001.tenant.model.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusService {
	
	 private final AccountRepository accountRepository;
	 
	 private final TenantSchemaProvisioningService tenantSchemaService;
	  private final TenantUserRepository tenantUserRepository;
	
	  @Transactional(transactionManager = "publicTransactionManager")
	  public AccountStatusChangeResponse changeAccountStatus(Long accountId, AccountStatusChangeRequest req) {
	    TenantSchemaContext.clearTenantSchema();

	    Account account = accountRepository.findById(accountId)
	            .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

	    if (account.isSystemAccount()) {
	        throw new ApiException("SYSTEM_ACCOUNT_PROTECTED",
	                "Contas do sistema não podem ter seu status alterado", 403);
	    }

	    AccountStatus previous = account.getStatus();
	    AccountStatus next = req.status();

	    if (previous == next) {
	        return buildStatusChangeResponse(account, previous, false, "NONE", 0);
	    }

	    if (previous == AccountStatus.CANCELLED) {
	        throw new ApiException("ACCOUNT_CANCELLED",
	                "Conta cancelada não pode ter status alterado", 409);
	    }

	    if (next == AccountStatus.FREE_TRIAL && previous != AccountStatus.FREE_TRIAL) {
	        throw new ApiException("INVALID_STATUS_TRANSITION",
	                "Não é permitido voltar para FREE_TRIAL", 409);
	    }

	    // 1) grava em PUBLIC
	    account.setStatus(next);
	    if (next == AccountStatus.ACTIVE) account.setDeletedAt(null);
	    accountRepository.save(account);

	    // 2) side-effects no tenant
	    int affected = 0;
	    boolean applied = false;
	    String action = "NONE";

	    if (next == AccountStatus.SUSPENDED) {
	        affected = suspendTenantUsersByAccount(account);
	        applied = true;
	        action = "SUSPEND_BY_ACCOUNT";
	    } else if (next == AccountStatus.ACTIVE) {
	        // ✅ reativar todos por conta, mantendo suspensosByAdmin
	        affected = unsuspendTenantUsersByAccount(account);
	        applied = true;
	        action = "UNSUSPEND_BY_ACCOUNT";
	    } else if (next == AccountStatus.CANCELLED) {
	        affected = cancelAccount(account);
	        applied = true;
	        action = "CANCELLED";
	    }

	    return buildStatusChangeResponse(account, previous, applied, action, affected);
	}
	  
	  

	    private int cancelAccount(Account account) {
	        // 1) PUBLIC
	        cancelAccountPublicTx(account);

	        // 2) TENANT (se existir)
	        String schema = account.getSchemaName();
	        boolean schemaOk = tenantSchemaService.validateTenantSchema(schema);
	        boolean tableOk = schemaOk && tenantSchemaService.tableExists(schema, "users_tenant");
	        if (!tableOk) return 0;

	        TenantSchemaContext.bindTenantSchema(schema);
	        try {
	            return cancelAccountTenantTx(account);
	        } finally {
	            TenantSchemaContext.clearTenantSchema();
	        }
	    }
	    

	    @Transactional(transactionManager = "publicTransactionManager", propagation = Propagation.REQUIRES_NEW)
	    protected void cancelAccountPublicTx(Account account) {
	        TenantSchemaContext.clearTenantSchema();
	        account.setDeletedAt(LocalDateTime.now());
	        accountRepository.save(account);
	    }
	    
	    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
	    protected int cancelAccountTenantTx(Account account) {
	        List<TenantUser> users = tenantUserRepository.findByAccountId(account.getId());
	        users.forEach(TenantUser::softDelete);
	        tenantUserRepository.saveAll(users);
	        return users.size();
	    }




	    private AccountStatusChangeResponse buildStatusChangeResponse(
	 	        Account account,
	 	        AccountStatus previous,
	 	        boolean tenantUsersUpdated,
	 	        String action,
	 	        int count
	 	) {
	 	    return new AccountStatusChangeResponse(
	 	            account.getId(),
	 	            account.getStatus().name(),
	 	            previous.name(),
	 	            LocalDateTime.now(),
	 	            account.getSchemaName(),
	 	            new AccountStatusChangeResponse.SideEffects(tenantUsersUpdated, action, count)
	 	    );
	 	}
	    
	    
	    
	    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
	    protected int suspendTenantUsersByAccount(Account account) {
	        String schema = account.getSchemaName();

	        if ("public".equals(schema)) return 0;
	        if (!tenantSchemaService.validateTenantSchema(schema)) return 0;
	        if (!tenantSchemaService.tableExists(schema, "users_tenant")) return 0;

	        TenantSchemaContext.bindTenantSchema(schema);
	        try {
	            // ✅ UPDATE em massa: suspendedByAccount = true, preserva suspendedByAdmin
	            return tenantUserRepository.suspendAllByAccount(account.getId());
	        } finally {
	            TenantSchemaContext.clearTenantSchema();
	        }
	    }

	    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
	    protected int unsuspendTenantUsersByAccount(Account account) {
	        String schema = account.getSchemaName();

	        if ("public".equals(schema)) return 0;
	        if (!tenantSchemaService.validateTenantSchema(schema)) return 0;
	        if (!tenantSchemaService.tableExists(schema, "users_tenant")) return 0;

	        TenantSchemaContext.bindTenantSchema(schema);
	        try {
	            // ✅ UPDATE em massa: suspendedByAccount = false, preserva suspendedByAdmin
	            return tenantUserRepository.unsuspendAllByAccount(account.getId());
	        } finally {
	            TenantSchemaContext.clearTenantSchema();
	        }
	    }
	    

	    @Transactional(transactionManager = "publicTransactionManager")
	    public void softDeleteAccount(Long accountId) {
	        TenantSchemaContext.clearTenantSchema();
	        Account account = getAccountById(accountId);

	        if (account.isSystemAccount()) {
	            throw new ApiException("SYSTEM_ACCOUNT_PROTECTED", 
	                    "Não é permitido excluir contas do sistema", 403);
	        }

	        softDeleteAccountTx(accountId);
	        softDeleteTenantUsersTx(accountId);
	    }

	    @Transactional(transactionManager = "publicTransactionManager")
	    protected void softDeleteAccountTx(Long accountId) {
	        TenantSchemaContext.clearTenantSchema();
	        Account account = getAccountById(accountId);
	        account.softDelete();
	        accountRepository.save(account);
	    }

	    @Transactional(transactionManager = "publicTransactionManager")
	    public void restoreAccount(Long accountId) {
	        TenantSchemaContext.clearTenantSchema();
	        Account account = getAccountById(accountId);

	        if (account.isSystemAccount() && account.isDeleted()) {
	            throw new ApiException("SYSTEM_ACCOUNT_PROTECTED",
	                    "Contas do sistema não podem ser restauradas via este endpoint", 403);
	        }

	        restoreAccountTx(accountId);
	        restoreTenantUsersTx(accountId);
	    }

	    @Transactional(transactionManager = "publicTransactionManager")
	    protected void restoreAccountTx(Long accountId) {
	        TenantSchemaContext.clearTenantSchema();
	        Account account = getAccountById(accountId);
	        account.restore();
	        accountRepository.save(account);
	    }
	    
	    @Transactional(transactionManager = "publicTransactionManager", readOnly = true)
	    public Account getAccountById(Long accountId) {
	        TenantSchemaContext.clearTenantSchema();
	        return accountRepository.findById(accountId)
	                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
	    }
	    
	    
	    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
	    protected void softDeleteTenantUsersTx(Long accountId) {
	        TenantSchemaContext.clearTenantSchema();
	        Account account = getAccountById(accountId);

	        if (!tenantSchemaService.validateTenantSchema(account.getSchemaName())) return;
	        if (!tenantSchemaService.tableExists(account.getSchemaName(), "users_tenant")) return;

	        TenantSchemaContext.bindTenantSchema(account.getSchemaName());
	        try {
	            List<TenantUser> users = tenantUserRepository.findByAccountId(account.getId());
	            users.forEach(u -> { if (!u.isDeleted()) u.softDelete(); });
	            tenantUserRepository.saveAll(users);
	        } finally {
	            TenantSchemaContext.clearTenantSchema();
	        }
	    }

	    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
	    protected void restoreTenantUsersTx(Long accountId) {
	        TenantSchemaContext.clearTenantSchema();
	        Account account = getAccountById(accountId);

	        if (!tenantSchemaService.validateTenantSchema(account.getSchemaName())) return;
	        if (!tenantSchemaService.tableExists(account.getSchemaName(), "users_tenant")) return;

	        TenantSchemaContext.bindTenantSchema(account.getSchemaName());
	        try {
	            List<TenantUser> users = tenantUserRepository.findByAccountId(account.getId());
	            users.forEach(u -> { if (u.isDeleted()) u.restore(); });
	            tenantUserRepository.saveAll(users);
	        } finally {
	            TenantSchemaContext.clearTenantSchema();
	        }
	    }





}
