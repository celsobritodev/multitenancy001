package brito.com.multitenancy001.controlplane.accounts.app.query;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.accounts.app.query.dto.AccountProvisioningEventData;
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
    public Page<AccountProvisioningEventData> listByAccount(Long accountId, Pageable pageable) {
        return repository.findByAccountId(accountId, pageable)
                .map(this::toData);
    }

    @Transactional(readOnly = true)
    public Optional<AccountProvisioningEventData> getLatestByAccount(
            Long accountId,
            ProvisioningStatus requireStatus
    ) {
        Optional<AccountProvisioningEvent> event;

        if (requireStatus == null) {
            event = repository.findTopByAccountIdOrderByCreatedAtDesc(accountId);
        } else {
            event = repository.findTopByAccountIdAndStatusOrderByCreatedAtDesc(accountId, requireStatus);
        }

        return event.map(this::toData);
    }

    private AccountProvisioningEventData toData(AccountProvisioningEvent e) {
        return new AccountProvisioningEventData(
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

