package brito.com.multitenancy001.controlplane.accounts.app.query;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.query.dto.AccountProvisioningEventData;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountProvisioningEvent;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountProvisioningEventRepository;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountProvisioningEventQueryService {

    private final PublicUnitOfWork publicUnitOfWork;
    private final AccountProvisioningEventRepository accountProvisioningEventRepository;

    public Page<AccountProvisioningEventData> listByAccount(Long accountId, Pageable pageable) {
        return publicUnitOfWork.readOnly(() ->
                accountProvisioningEventRepository.findByAccountId(accountId, pageable)
                        .map(this::toData)
        );
    }

    public Optional<AccountProvisioningEventData> getLatestByAccount(
            Long accountId,
            ProvisioningStatus requireStatus
    ) {
        return publicUnitOfWork.readOnly(() -> {
            Optional<AccountProvisioningEvent> event;

            if (requireStatus == null) {
                event = accountProvisioningEventRepository.findTopByAccountIdOrderByCreatedAtDesc(accountId);
            } else {
                event = accountProvisioningEventRepository.findTopByAccountIdAndStatusOrderByCreatedAtDesc(accountId, requireStatus);
            }

            return event.map(this::toData);
        });
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
