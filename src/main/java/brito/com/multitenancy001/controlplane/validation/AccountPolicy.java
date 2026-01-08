package brito.com.multitenancy001.controlplane.validation;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccountPolicy {
    
    private final AccountRepository accountRepository;
    
    /**
     * Valida se a conta pode ser gerenciada (não é conta do sistema)
     */
    public void validateNotSystemAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ApiException(
                "ACCOUNT_NOT_FOUND",
                "Conta não encontrada",
                404
            ));
        
        if (account.isSystemAccount()) {
            throw new ApiException(
                "SYSTEM_ACCOUNT_PROTECTED",
                "Operação não permitida para contas do sistema",
                403
            );
        }
    }
    
    /**
     * Valida se a conta pode ser gerenciada (não é conta do sistema)
     */
    public void validateNotSystemAccount(Account account) {
        if (account.isSystemAccount()) {
            throw new ApiException(
                "SYSTEM_ACCOUNT_PROTECTED",
                "Operação não permitida para contas do sistema",
                403
            );
        }
    }
}