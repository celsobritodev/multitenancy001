package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

/**
 * Validator central do domínio de subscription (Control Plane).
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Eliminar validações inline repetidas</li>
 *   <li>Padronizar mensagens e códigos</li>
 *   <li>Evitar divergência futura</li>
 * </ul>
 */
public final class SubscriptionValidator {

    private SubscriptionValidator() {
    }

    public static void requireAccount(Account account) {
        if (account == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Conta é obrigatória");
        }
    }

    public static void requireAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório");
        }
    }

    public static void requireSubscriptionPlan(SubscriptionPlan plan) {
        if (plan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "subscriptionPlan é obrigatório");
        }
    }

    public static void requireTargetPlan(SubscriptionPlan plan) {
        if (plan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório");
        }
    }

    public static void requireCommand(Object command) {
        if (command == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "command é obrigatório");
        }
    }

    public static void requireUsageSnapshot(PlanUsageSnapshot snapshot) {
        if (snapshot == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "usageSnapshot é obrigatório");
        }
    }

    public static void requireCurrentPlan(SubscriptionPlan plan) {
        if (plan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "currentPlan é obrigatório");
        }
    }
}