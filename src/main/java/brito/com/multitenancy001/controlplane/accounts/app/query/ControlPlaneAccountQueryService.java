package brito.com.multitenancy001.controlplane.accounts.app.query;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ControlPlaneAccountQueryService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Account getEnabledById(Long id) {
        if (id == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "id é obrigatório", 400);

        return accountRepository.findEnabledById(id)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_ENABLED", "Conta não encontrada ou não operacional", 404));
    }

    @Transactional(readOnly = true)
    public Account getAnyById(Long id) {
        if (id == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "id é obrigatório", 400);

        return accountRepository.findAnyById(id)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
    }

    @Transactional(readOnly = true)
    public long countByStatusesNotDeleted(List<AccountStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            throw new ApiException("ACCOUNT_STATUSES_REQUIRED", "statuses é obrigatório", 400);
        }
        return accountRepository.countByStatusesAndDeletedFalse(statuses);
    }

    @Transactional(readOnly = true)
    public List<Account> findPaymentDueBeforeNotDeleted(LocalDateTime date) {
        if (date == null) throw new ApiException("DATE_REQUIRED", "date é obrigatório", 400);

        return accountRepository.findByPaymentDueDateBeforeAndDeletedFalse(date);
    }
}
