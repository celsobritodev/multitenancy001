package brito.com.multitenancy001.controlplane.billing.app;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por enfileirar pagamentos que exigem binding de upgrade.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Receber pagamentos concluídos que exigem mudança de plano.</li>
 *   <li>Evitar duplicação de enfileiramento para o mesmo pagamento.</li>
 *   <li>Centralizar logs e critérios de entrada na fila.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Esta implementação usa fila em memória.</li>
 *   <li>É um passo intermediário antes da implantação de worker idempotente dedicado.</li>
 * </ul>
 */
@Service
@Slf4j
public class ControlPlanePaymentUpgradeEnqueueService {

    /**
     * Fila em memória para pagamentos que exigem binding de upgrade.
     */
    private final ConcurrentLinkedQueue<Long> upgradeQueue = new ConcurrentLinkedQueue<>();

    /**
     * Conjunto auxiliar para evitar enfileiramento duplicado.
     */
    private final Set<Long> queuedPaymentIds = ConcurrentHashMap.newKeySet();

    /**
     * Enfileira pagamento quando houver binding de plano.
     *
     * @param payment pagamento finalizado
     */
    public void enqueueIfRequired(Payment payment) {
        if (payment == null) {
            return;
        }

        if (!payment.requiresPlanBinding()) {
            return;
        }

        if (!payment.isCompleted()) {
            log.warn("Pagamento com binding de plano ainda não está COMPLETED. paymentId={}, status={}",
                    payment.getId(),
                    payment.getStatus());
            return;
        }

        if (payment.getTargetPlan() == null) {
            log.warn("Pagamento de upgrade sem targetPlan. paymentId={}", payment.getId());
            return;
        }

        if (queuedPaymentIds.add(payment.getId())) {
            upgradeQueue.add(payment.getId());
            log.info("Pagamento de upgrade enfileirado. paymentId={}", payment.getId());
        } else {
            log.info("Pagamento de upgrade já estava enfileirado. paymentId={}", payment.getId());
        }
    }

    /**
     * Informa se um pagamento já está marcado como enfileirado.
     *
     * @param paymentId id do pagamento
     * @return {@code true} quando já enfileirado
     */
    public boolean isQueued(Long paymentId) {
        return paymentId != null && queuedPaymentIds.contains(paymentId);
    }

    /**
     * Retorna a fila interna em memória.
     *
     * <p>Uso restrito para futura integração com worker ou diagnóstico interno.</p>
     *
     * @return fila de ids de pagamento
     */
    public ConcurrentLinkedQueue<Long> getUpgradeQueue() {
        return upgradeQueue;
    }
}