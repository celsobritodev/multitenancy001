package brito.com.multitenancy001.controlplane.billing.app;

import java.time.Instant;

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
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de ciclo de vida de pagamentos do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Criar pagamentos PENDING em transação PUBLIC.</li>
 *   <li>Aplicar idempotência forte via busca por {@code idempotencyKey}
 *       e reaproveitamento após colisão de UNIQUE no banco.</li>
 *   <li>Finalizar pagamentos com lock pessimista.</li>
 *   <li>Marcar pagamentos como falhos usando invariantes do domínio.</li>
 *   <li>Buscar pagamento por chave de idempotência.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlanePaymentLifecycleService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlanePaymentRepository controlPlanePaymentRepository;

    /**
     * Busca pagamento por chave de idempotência.
     *
     * @param idempotencyKey chave normalizada
     * @return pagamento encontrado ou {@code null}
     */
    public Payment findByIdempotency(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findByIdempotencyKeyWithAccount(idempotencyKey).orElse(null)
        );
    }

    /**
     * Cria pagamento administrativo em transação PUBLIC.
     *
     * <p>Se houver colisão real de {@code idempotencyKey}, reaproveita o pagamento
     * já persistido no banco, garantindo retry-safe.</p>
     *
     * @param adminPaymentRequest request administrativo
     * @param now instante atual
     * @param idempotencyKey chave de idempotência normalizada
     * @return pagamento persistido ou reaproveitado
     */
    public Payment createPaymentAdmin(
            AdminPaymentRequest adminPaymentRequest,
            Instant now,
            String idempotencyKey
    ) {
        return publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findById(adminPaymentRequest.accountId())
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada",
                            404
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

    /**
     * Cria pagamento self-service em transação PUBLIC.
     *
     * <p>Se houver colisão real de {@code idempotencyKey}, reaproveita o pagamento
     * já persistido no banco, garantindo retry-safe.</p>
     *
     * @param accountId conta autenticada
     * @param paymentRequest request self-service
     * @param now instante atual
     * @param idempotencyKey chave de idempotência normalizada
     * @return pagamento persistido ou reaproveitado
     */
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
                            "Conta não encontrada",
                            404
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

    /**
     * Finaliza pagamento em transação isolada com lock pessimista.
     *
     * @param paymentId id do pagamento
     * @param now instante atual
     * @return pagamento finalizado
     */
    public Payment finalizePayment(Long paymentId, Instant now) {
        return publicSchemaUnitOfWork.requiresNew(() -> {
            Payment payment = controlPlanePaymentRepository.findByIdWithAccountForUpdate(paymentId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.PAYMENT_NOT_FOUND,
                            "Pagamento não encontrado",
                            404
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

    /**
     * Marca pagamento como falho usando invariante do domínio.
     *
     * @param paymentId id do pagamento
     * @param reason motivo técnico/funcional
     */
    public void failPayment(Long paymentId, String reason) {
        publicSchemaUnitOfWork.tx(() -> {
            Payment payment = controlPlanePaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.PAYMENT_NOT_FOUND,
                            "Pagamento não encontrado",
                            404
                    ));

            payment.markAsFailed(reason);
            controlPlanePaymentRepository.save(payment);

            log.warn("Pagamento marcado como FAILED. paymentId={}, reason={}",
                    paymentId,
                    reason);
            return null;
        });
    }

    /**
     * Normaliza string opcional.
     *
     * @param value valor bruto
     * @return valor normalizado ou {@code null}
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}