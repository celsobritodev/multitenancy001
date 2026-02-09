package brito.com.multitenancy001.controlplane.accounts.app.audit;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountProvisioningEvent;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountProvisioningEventRepository;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountProvisioningAuditService {

    private final PublicUnitOfWork publicUnitOfWork;
    private final AccountProvisioningEventRepository accountProvisioningEventRepository;
    private final AppClock appClock;

    public void started(Long accountId, String message, String detailsJson) {
        record(accountId, ProvisioningStatus.STARTED, null, message, detailsJson);
    }

    public void success(Long accountId, String message, String detailsJson) {
        record(accountId, ProvisioningStatus.SUCCESS, null, message, detailsJson);
    }

    public void failed(Long accountId, ProvisioningFailureCode failureCode, String message, String detailsJson) {
        record(accountId, ProvisioningStatus.FAILED, failureCode, message, detailsJson);
    }

    private void record(
            Long accountId,
            ProvisioningStatus status,
            ProvisioningFailureCode failureCode,
            String message,
            String detailsJson
    ) {
        // ✅ write-capable TX no public schema
        publicUnitOfWork.tx(() -> {
            AccountProvisioningEvent e = new AccountProvisioningEvent(
                    accountId,
                    status,
                    failureCode,
                    message,
                    detailsJson,
                    appClock.instant()
            );
            accountProvisioningEventRepository.save(e);
        });
    }
}
