// src/main/java/brito/com/multitenancy001/shared/auth/domain/AuthSessionDomain.java
package brito.com.multitenancy001.shared.auth.domain;

/**
 * Domínio (contexto) de uma sessão de autenticação/refresh.
 *
 * Regras:
 * - TENANT: sessão do contexto "tenant" (exige tenantSchema preenchido).
 * - CONTROLPLANE: sessão do contexto "control plane" (tenantSchema deve ser null).
 */
public enum AuthSessionDomain {
    TENANT,
    CONTROLPLANE
}