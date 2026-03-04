// src/main/java/brito/com/multitenancy001/shared/domain/service/LoginIdentityService.java
package brito.com.multitenancy001.shared.domain.service;

import brito.com.multitenancy001.shared.kernel.error.ApiException;

/**
 * DOMAIN SERVICE (SHARED)
 * 
 * Responsável por garantir que a identidade de login (public.login_identities)
 * esteja disponível para consulta.
 * 
 * Esta interface representa uma DEPENDÊNCIA do domínio para a infraestrutura.
 * A implementação concreta deve garantir que a operação seja:
 * - IDEMPOTENTE: pode ser chamada múltiplas vezes sem efeitos colaterais
 * - ATÔMICA: ou completa com sucesso ou falha com exceção clara
 * - CONSISTENTE: após o retorno, a identidade DEVE estar disponível para consulta
 */
public interface LoginIdentityService {

    /**
     * Garante que a identidade de login para um usuário de tenant exista.
     * 
     * @param email email do usuário (já normalizado)
     * @param accountId ID da conta
     * @throws ApiException se não for possível garantir a identidade
     */
    void ensureTenantIdentity(String email, Long accountId);

    /**
     * Garante que a identidade de login para um usuário do Control Plane exista.
     * 
     * @param email email do usuário (já normalizado)
     * @param userId ID do usuário do Control Plane
     * @throws ApiException se não for possível garantir a identidade
     */
    void ensureControlPlaneIdentity(String email, Long userId);

    /**
     * Remove a identidade de login de um usuário de tenant.
     * 
     * @param email email do usuário (já normalizado)
     * @param accountId ID da conta
     */
    void deleteTenantIdentity(String email, Long accountId);

    /**
     * Remove a identidade de login de um usuário do Control Plane por userId.
     * 
     * @param userId ID do usuário do Control Plane
     */
    void deleteControlPlaneIdentityByUserId(Long userId);

    /**
     * Move a identidade de login de um usuário do Control Plane para um novo email.
     * 
     * @param userId ID do usuário do Control Plane
     * @param newEmail novo email (já normalizado)
     */
    void moveControlPlaneIdentity(Long userId, String newEmail);
}