package brito.com.multitenancy001.controlplane.accounts.app.query;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountResponse;
import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ControlPlaneAccountQueryService {

    private final AccountRepository accountRepository;
    private final AccountApiMapper accountApiMapper;

    @Transactional(readOnly = true)
    public AccountResponse getEnabledById(Long id) {
        if (id == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "id é obrigatório", 400);

        Account a = accountRepository.findEnabledById(id)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_ENABLED", "Conta não encontrada ou não operacional", 404));

        return accountApiMapper.toResponse(a);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAnyById(Long id) {
        if (id == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "id é obrigatório", 400);

        Account a = accountRepository.findAnyById(id)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

        return accountApiMapper.toResponse(a);
    }

    @Transactional(readOnly = true)
    public long countByStatusesNotDeleted(List<AccountStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            throw new ApiException("ACCOUNT_STATUSES_REQUIRED", "statuses é obrigatório", 400);
        }
        return accountRepository.countByStatusesAndDeletedFalse(statuses);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findPaymentDueBeforeNotDeleted(LocalDateTime date) {
        if (date == null) throw new ApiException("DATE_REQUIRED", "date é obrigatório", 400);

        return accountRepository.findByPaymentDueDateBeforeAndDeletedFalse(date)
                .stream()
                .map(accountApiMapper::toResponse)
                .toList();
    }
}
