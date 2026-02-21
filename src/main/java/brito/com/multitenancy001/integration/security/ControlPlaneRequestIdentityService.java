package brito.com.multitenancy001.integration.security;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Integração: ControlPlane -> Infrastructure (SecurityUtils).
 *
 * Objetivo:
 * - Evitar ControlPlane importar infrastructure.security.* diretamente.
 * - Expôr identidade do request de forma mínima e semântica.
 *
 * Regras:
 * - Métodos aqui são "façade" do SecurityUtils.
 * - Não adiciona lógica de negócio; apenas expõe dados do contexto de segurança.
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneRequestIdentityService {

    private final SecurityUtils securityUtils;

    public Long getCurrentAccountId() {
        /* Retorna accountId do usuário autenticado no contexto atual. */
        return securityUtils.getCurrentAccountId();
    }

    public Long getCurrentUserId() {
        /* Retorna userId do usuário autenticado no contexto atual. */
        return securityUtils.getCurrentUserId();
    }

    public String getCurrentEmail() {
        /* Retorna email (ou name fallback) do usuário autenticado no contexto atual. */
        return securityUtils.getCurrentEmail();
    }

    public String getCurrentPrincipal() {
        /* Alias semântico: “principal/login do request” (não compromete nome/email). */
        return securityUtils.getCurrentEmail();
    }
}