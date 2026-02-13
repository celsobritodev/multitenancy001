package brito.com.multitenancy001.controlplane.accounts.app.query;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ControlPlaneAccountQueryService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;

    public Account getEnabledById(Long id) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (id == null) {
                // validação -> category VALIDATION
                throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "id é obrigatório");
            }

            return accountRepository.findEnabledById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_ENABLED));
        });
    }

    public Account getAnyById(Long id) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (id == null) {
                throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "id é obrigatório");
            }

            return accountRepository.findAnyById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND));
        });
    }

    public long countByStatusesNotDeleted(List<AccountStatus> statuses) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (statuses == null || statuses.isEmpty()) {
                throw new ApiException(ApiErrorCode.ACCOUNT_STATUSES_REQUIRED, "statuses é obrigatório");
            }
            return accountRepository.countByStatusesAndDeletedFalse(statuses);
        });
    }

    public List<Account> findPaymentDueBeforeNotDeleted(LocalDate date) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (date == null) {
                throw new ApiException(ApiErrorCode.DATE_REQUIRED);
            }
            return accountRepository.findByPaymentDueDateBeforeAndDeletedFalse(date);
        });
    }
}

