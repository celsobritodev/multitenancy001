package brito.com.multitenancy001.controlplane.accounts.app.query;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountProvisioningEventResponse;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountProvisioningEvent;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountProvisioningEventRepository;

@Service
public class AccountProvisioningEventQueryService {

    private final AccountProvisioningEventRepository repository;

    public AccountProvisioningEventQueryService(AccountProvisioningEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AccountProvisioningEventResponse> listByAccount(Long accountId, Pageable pageable) {
        return repository.findByAccountId(accountId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<AccountProvisioningEventResponse> getLatestByAccount(
            Long accountId,
            ProvisioningStatus requireStatus
    ) {
        Optional<AccountProvisioningEvent> event;

        if (requireStatus == null) {
            event = repository.findTopByAccountIdOrderByCreatedAtDesc(accountId);
        } else {
            event = repository.findTopByAccountIdAndStatusOrderByCreatedAtDesc(
                    accountId,
                    requireStatus
            );
        }

        return event.map(this::toResponse);
    }

    private AccountProvisioningEventResponse toResponse(AccountProvisioningEvent e) {
        return new AccountProvisioningEventResponse(
                e.getId(),
                e.getAccountId(),
                e.getStatus(),
                parseFailureCodeOrNull(e.getFailureCode()),
                e.getMessage(),
                e.getDetailsJson(),
                e.getCreatedAt()
        );
    }

    private ProvisioningFailureCode parseFailureCodeOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ProvisioningFailureCode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
