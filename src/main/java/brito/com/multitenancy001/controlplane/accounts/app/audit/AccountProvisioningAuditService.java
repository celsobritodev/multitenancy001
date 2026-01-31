package brito.com.multitenancy001.controlplane.accounts.app.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountProvisioningEvent;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountProvisioningEventRepository;

@Service
public class AccountProvisioningAuditService {

    private final AccountProvisioningEventRepository repository;

    public AccountProvisioningAuditService(AccountProvisioningEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logStarted(Long accountId, String detailsJson) {
        repository.save(new AccountProvisioningEvent(
                accountId,
                "STARTED",
                null,
                "Provisioning started",
                detailsJson
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(Long accountId, String detailsJson) {
        repository.save(new AccountProvisioningEvent(
                accountId,
                "SUCCESS",
                null,
                "Provisioning succeeded",
                detailsJson
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(Long accountId, ProvisioningFailureCode code, String message, String detailsJson) {
        repository.save(new AccountProvisioningEvent(
                accountId,
                "FAILED",
                code,
                message,
                detailsJson
        ));
    }
}
