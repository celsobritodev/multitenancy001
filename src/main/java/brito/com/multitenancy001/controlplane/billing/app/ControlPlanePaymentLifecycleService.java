package brito.com.multitenancy001.controlplane.billing.app;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import brito.com.multitenancy001.controlplane.billing.persistence.ControlPlanePaymentRepository;
import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de ciclo de vida de pagamentos do Control Plane.
 *
 * <p>Regra V33:</p>
 * <ul>
 *   <li>Sem status HTTP hardcoded.</li>
 *   <li>Idempotência forte preservada.</li>
 *   <li>Sem montagem manual de JSON no domínio.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlanePaymentLifecycleService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlanePaymentRepository controlPlanePaymentRepository;
    private final JsonDetailsMapper jsonDetailsMapper;

    public Payment findByIdempotency(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findByIdempotencyKeyWithAccount(idempotencyKey).orElse(null)
        );
    }

    public Payment createPaymentAdmin(
            AdminPaymentRequest adminPaymentRequest,
            Instant now,
            String idempotencyKey
    ) {
        return publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findById(adminPaymentRequest.accountId())
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada"
                    ));

            Payment payment = Payment.builder()
                    .account(account)
                    .amount(adminPaymentRequest.amount())
                    .paymentMethod(adminPaymentRequest.paymentMethod())
                    .paymentGateway(adminPaymentRequest.paymentGateway())
                    .description(normalize(adminPaymentRequest.description()))
                    .status(PaymentStatus.PENDING)
                    .paymentDate(now)
                    .targetPlan(adminPaymentRequest.targetPlan())
                    .billingCycle(adminPaymentRequest.billingCycle())
                    .paymentPurpose(adminPaymentRequest.purpose())
                    .planPriceSnapshot(adminPaymentRequest.planPriceSnapshot())
                    .currency(adminPaymentRequest.currencyCode())
                    .effectiveFrom(adminPaymentRequest.effectiveFrom())
                    .coverageEndDate(adminPaymentRequest.coverageEndDate())
                    .validUntil(adminPaymentRequest.coverageEndDate())
                    .idempotencyKey(idempotencyKey)
                    .build();

            try {
                Payment saved = controlPlanePaymentRepository.save(payment);

                log.info("Pagamento administrativo criado. paymentId={}, accountId={}, idempotencyKey={}",
                        saved.getId(),
                        account.getId(),
                        saved.getIdempotencyKey());

                return saved;
            } catch (DataIntegrityViolationException ex) {
                if (!StringUtils.hasText(idempotencyKey)) {
                    throw ex;
                }

                log.warn("Colisão de idempotência detectada na criação ADMIN. accountId={}, idempotencyKey={}",
                        account.getId(),
                        idempotencyKey);

                return controlPlanePaymentRepository.findByIdempotencyKeyWithAccount(idempotencyKey)
                        .orElseThrow(() -> ex);
            }
        });
    }

    public Payment createPaymentSelf(
            Long accountId,
            PaymentRequest paymentRequest,
            Instant now,
            String idempotencyKey
    ) {
        return publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada"
                    ));

            Payment payment = Payment.builder()
                    .account(account)
                    .amount(paymentRequest.amount())
                    .paymentMethod(paymentRequest.paymentMethod())
                    .paymentGateway(paymentRequest.paymentGateway())
                    .description(normalize(paymentRequest.description()))
                    .status(PaymentStatus.PENDING)
                    .paymentDate(now)
                    .targetPlan(paymentRequest.targetPlan())
                    .billingCycle(paymentRequest.billingCycle())
                    .paymentPurpose(paymentRequest.purpose())
                    .planPriceSnapshot(paymentRequest.planPriceSnapshot())
                    .currency(paymentRequest.currencyCode())
                    .effectiveFrom(paymentRequest.effectiveFrom())
                    .coverageEndDate(paymentRequest.coverageEndDate())
                    .validUntil(paymentRequest.coverageEndDate())
                    .idempotencyKey(idempotencyKey)
                    .build();

            try {
                Payment saved = controlPlanePaymentRepository.save(payment);

                log.info("Pagamento self-service criado. paymentId={}, accountId={}, idempotencyKey={}",
                        saved.getId(),
                        account.getId(),
                        saved.getIdempotencyKey());

                return saved;
            } catch (DataIntegrityViolationException ex) {
                if (!StringUtils.hasText(idempotencyKey)) {
                    throw ex;
                }

                log.warn("Colisão de idempotência detectada na criação SELF. accountId={}, idempotencyKey={}",
                        account.getId(),
                        idempotencyKey);

                return controlPlanePaymentRepository.findByIdempotencyKeyWithAccount(idempotencyKey)
                        .orElseThrow(() -> ex);
            }
        });
    }

    public Payment finalizePayment(Long paymentId, Instant now) {
        return publicSchemaUnitOfWork.requiresNew(() -> {
            Payment payment = controlPlanePaymentRepository.findByIdWithAccountForUpdate(paymentId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.PAYMENT_NOT_FOUND,
                            "Pagamento não encontrado"
                    ));

            if (payment.isCompleted()) {
                log.info("Pagamento já estava completado. paymentId={}", paymentId);
                return payment;
            }

            payment.markAsCompleted(now);
            Payment saved = controlPlanePaymentRepository.save(payment);

            log.info("Pagamento finalizado com sucesso. paymentId={}, status={}",
                    saved.getId(),
                    saved.getStatus());

            return saved;
        });
    }

    public void failPayment(Long paymentId, String reason) {
        publicSchemaUnitOfWork.tx(() -> {
            Payment payment = controlPlanePaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.PAYMENT_NOT_FOUND,
                            "Pagamento não encontrado"
                    ));

            String metadataJson = jsonDetailsMapper.toJson(
                    failureMetadata(reason)
            );

            payment.markAsFailed(reason, metadataJson);
            controlPlanePaymentRepository.save(payment);

            log.warn("Pagamento marcado como FAILED. paymentId={}, reason={}",
                    paymentId,
                    reason);
            return null;
        });
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<String, Object> failureMetadata(String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("failure_reason", reason);
        return details;
    }
}