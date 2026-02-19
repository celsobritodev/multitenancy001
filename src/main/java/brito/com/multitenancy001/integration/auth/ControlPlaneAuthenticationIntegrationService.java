package brito.com.multitenancy001.integration.auth;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.userdetails.MultiContextUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Integração: ControlPlane -> Infrastructure.
 *
 * Objetivo:
 * - Permitir que o ControlPlane monte um Authentication "válido" (principal + authorities)
 *   sem importar classes de infrastructure diretamente.
 *
 * Uso típico:
 * - Refresh: reconstruir Authentication a partir do email/accountId do refresh token
 *   para gerar um novo access token.
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneAuthenticationIntegrationService {

    private final MultiContextUserDetailsService multiContextUserDetailsService;

    public Authentication buildControlPlaneAuthentication(String email, Long accountId) {
        /** comentário: carrega principal CP (com authorities) e cria Authentication */
        AuthenticatedUserContext principal = (AuthenticatedUserContext)
                multiContextUserDetailsService.loadControlPlaneUserByEmail(email, accountId);

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
    }
}
