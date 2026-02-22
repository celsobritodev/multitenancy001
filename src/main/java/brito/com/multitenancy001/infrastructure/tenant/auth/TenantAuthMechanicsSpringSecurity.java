package brito.com.multitenancy001.infrastructure.tenant.auth;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantRefreshIdentity;
import brito.com.multitenancy001.tenant.security.TenantRoleMapper;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Implementação do TenantAuthMechanics usando Repository + PasswordEncoder + JWT.
 *
 * Motivação (Rota 1):
 * - Em schema-per-tenant, o caminho AuthenticationManager -> UserDetailsService pode cair em um EntityManager
 *   sem mapeamento de TenantUser e falhar com UnknownEntityException (convertendo em 401 indevido).
 * - Para destravar E2E de forma robusta, validamos credenciais via repository no schema do tenant
 *   e usamos passwordEncoder.matches(raw, encoded).
 *
 * Ajustes:
 * - resolveRefreshIdentity(refreshToken) NÃO faz query (somente parse/validação JWT)
 * - refreshTenantJwt(refreshToken) faz 1 query e emite NOVO refresh token (rotação)
 */
@Component
@RequiredArgsConstructor
public class TenantAuthMechanicsSpringSecurity implements TenantAuthMechanics {

    private static final String INVALID_CREDENTIALS_MSG = "usuario ou senha invalidos";

    private final TenantExecutor tenantExecutor;
    private final TenantUserRepository tenantUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    @Override
    public boolean verifyPasswordInTenant(AccountSnapshot account, String normalizedEmail, String rawPassword) {
        /** comentário: valida senha dentro do schema do tenant */
        if (account == null || account.id() == null) return false;

        String tenantSchema = account.tenantSchema();
        if (!StringUtils.hasText(tenantSchema)) return false;

        tenantSchema = tenantSchema.trim();

        if (!StringUtils.hasText(normalizedEmail) || !StringUtils.hasText(rawPassword)) return false;

        String finalTenantSchema = tenantSchema;

        try {
            return tenantExecutor.runInTenantSchema(finalTenantSchema, () -> {

                TenantUser user = tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                        .orElse(null);

                if (user == null) return false;

                // Opcional: não vaza motivo, só retorna false
                if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) return false;

                String encoded = user.getPassword(); // ajuste se seu entity usar outro nome
                if (!StringUtils.hasText(encoded)) return false;

                return passwordEncoder.matches(rawPassword, encoded);
            });
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public JwtResult authenticateWithPassword(AccountSnapshot account, String normalizedEmail, String rawPassword) {
        /** comentário: autentica com senha e emite access+refresh (sem AuthenticationManager) */
        if (account == null || account.id() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404);
        }

        String tenantSchema = account.tenantSchema();
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_READY, "Conta sem schema", 409);
        }

        tenantSchema = tenantSchema.trim();

        if (!StringUtils.hasText(normalizedEmail) || !StringUtils.hasText(rawPassword)) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "email e senha são obrigatórios", 400);
        }

        String finalTenantSchema = tenantSchema;

        try {
            return tenantExecutor.runInTenantSchema(finalTenantSchema, () -> {

                TenantUser user = tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_USER, INVALID_CREDENTIALS_MSG, 401));

                ensureUserActive(user);

                String encoded = user.getPassword(); // ajuste se seu entity usar outro nome
                if (!StringUtils.hasText(encoded) || !passwordEncoder.matches(rawPassword, encoded)) {
                    throw new ApiException(ApiErrorCode.INVALID_USER, INVALID_CREDENTIALS_MSG, 401);
                }

                tenantUserRepository.updateLastLogin(user.getId(), appClock.instant());

                var authorities = AuthoritiesFactory.forTenant(user);

                AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                        user,
                        finalTenantSchema,
                        appClock.instant(),
                        authorities
                );

                var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

                String accessToken = jwtTokenProvider.generateTenantToken(authentication, account.id(), finalTenantSchema);

                String refreshToken = jwtTokenProvider.generateRefreshToken(
                        user.getEmail(),
                        finalTenantSchema,
                        account.id()
                );

                SystemRoleName role = TenantRoleMapper.toSystemRoleOrNull(user.getRole());

                return new JwtResult(
                        accessToken,
                        refreshToken,
                        user.getId(),
                        user.getEmail(),
                        role,
                        account.id(),
                        finalTenantSchema
                );
            });
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.AUTH_ERROR, "Falha ao autenticar", 500);
        }
    }

    @Override
    public JwtResult issueJwtForAccountAndEmail(AccountSnapshot account, String normalizedEmail) {
        /** comentário: emite tokens sem senha (confirm) */
        if (account == null || account.id() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404);
        }

        String tenantSchema = account.tenantSchema();
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_READY, "Conta sem schema", 409);
        }

        tenantSchema = tenantSchema.trim();

        if (!StringUtils.hasText(normalizedEmail)) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "email é obrigatório", 400);
        }

        String finalTenantSchema = tenantSchema;

        return tenantExecutor.runInTenantSchema(finalTenantSchema, () -> {

            TenantUser user = tenantUserRepository
                    .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_LOGIN, "Usuário não encontrado", 401));

            ensureUserActive(user);

            tenantUserRepository.updateLastLogin(user.getId(), appClock.instant());

            var authorities = AuthoritiesFactory.forTenant(user);

            AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                    user,
                    finalTenantSchema,
                    appClock.instant(),
                    authorities
            );

            var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );

            String accessToken = jwtTokenProvider.generateTenantToken(authentication, account.id(), finalTenantSchema);

            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getEmail(),
                    finalTenantSchema,
                    account.id()
            );

            SystemRoleName role = TenantRoleMapper.toSystemRoleOrNull(user.getRole());

            return new JwtResult(
                    accessToken,
                    refreshToken,
                    user.getId(),
                    user.getEmail(),
                    role,
                    account.id(),
                    finalTenantSchema
            );
        });
    }

    @Override
    public TenantRefreshIdentity resolveRefreshIdentity(String refreshToken) {
        /** comentário: valida refresh e resolve identidade mínima (sem query) */
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório", 400);
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }

        AuthDomain authDomain = jwtTokenProvider.getAuthDomainEnum(refreshToken);
        if (authDomain != AuthDomain.REFRESH) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }

        final String tenantSchemaRaw = jwtTokenProvider.getTenantSchemaFromToken(refreshToken);
        if (!StringUtils.hasText(tenantSchemaRaw)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }
        final String tenantSchema = tenantSchemaRaw.trim();

        final String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (email ausente)", 401);
        }

        final Long accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (accountId ausente)", 401);
        }

        return new TenantRefreshIdentity(email.trim(), accountId, tenantSchema);
    }

    @Override
    public JwtResult refreshTenantJwt(String refreshToken) {
        /** comentário: refresh do tenant com rotação (novo refresh token) */
        TenantRefreshIdentity id = resolveRefreshIdentity(refreshToken);

        return tenantExecutor.runInTenantSchema(id.tenantSchema(), () -> {

            TenantUser user = tenantUserRepository
                    .findByEmailAndAccountIdAndDeletedFalse(id.email(), id.accountId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401));

            ensureUserActive(user);

            tenantUserRepository.updateLastLogin(user.getId(), appClock.instant());

            var authorities = AuthoritiesFactory.forTenant(user);

            AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                    user,
                    id.tenantSchema(),
                    appClock.instant(),
                    authorities
            );

            var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );

            String newAccessToken = jwtTokenProvider.generateTenantToken(authentication, id.accountId(), id.tenantSchema());

            String newRefreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getEmail(),
                    id.tenantSchema(),
                    id.accountId()
            );

            SystemRoleName role = TenantRoleMapper.toSystemRoleOrNull(user.getRole());

            return new JwtResult(
                    newAccessToken,
                    newRefreshToken,
                    user.getId(),
                    user.getEmail(),
                    role,
                    id.accountId(),
                    id.tenantSchema()
            );
        });
    }

    private static void ensureUserActive(TenantUser user) {
        /** comentário: bloqueia usuário suspenso/inativo/deletado */
        if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
            throw new ApiException(ApiErrorCode.USER_INACTIVE, "Usuário inativo", 403);
        }
    }
}