package brito.com.multitenancy001.tenant.users.api.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.tenant.me.api.dto.TenantMeResponse;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.api.dto.TenantActorRef;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserListItemResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.extern.slf4j.Slf4j;

/**
 * Mapper da API tenant para transformação de {@link TenantUser} em DTOs de resposta.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Converter entidade de domínio em DTOs HTTP do módulo de usuários.</li>
 *   <li>Centralizar a regra de cálculo de {@code enabled} para respostas da API.</li>
 *   <li>Normalizar a serialização de permissões em responses ricos.</li>
 *   <li>Mapear metadados de auditoria para referências de ator.</li>
 * </ul>
 *
 * <p><b>Diretrizes:</b></p>
 * <ul>
 *   <li>O mapper não executa regra de negócio de persistência.</li>
 *   <li>O mapper não acessa repositórios.</li>
 *   <li>O mapper deve ser tolerante a nulos em coleções e blocos de auditoria.</li>
 * </ul>
 */
@Component
@Slf4j
public class TenantUserApiMapper {

    /**
     * Converte usuário em response resumido.
     *
     * @param tenantUser entidade de usuário
     * @return DTO resumido
     */
    public TenantUserSummaryResponse toSummary(TenantUser tenantUser) {
        validateUser(tenantUser);

        boolean enabled = calculateEnabled(tenantUser);

        return new TenantUserSummaryResponse(
                tenantUser.getId(),
                tenantUser.getEmail(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                enabled
        );
    }

    /**
     * Converte usuário em response do endpoint /me.
     *
     * @param tenantUser entidade de usuário
     * @return DTO do usuário autenticado
     */
    public TenantMeResponse toMe(TenantUser tenantUser) {
        validateUser(tenantUser);

        boolean enabled = calculateEnabled(tenantUser);
        TenantRole role = tenantUser.getRole();

        return new TenantMeResponse(
                tenantUser.getId(),
                tenantUser.getAccountId(),
                tenantUser.getName(),
                tenantUser.getEmail(),
                role,
                tenantUser.getPhone(),
                tenantUser.getAvatarUrl(),
                tenantUser.getTimezone(),
                tenantUser.getLocale(),
                tenantUser.isMustChangePassword(),
                tenantUser.getOrigin(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                tenantUser.isDeleted(),
                enabled
        );
    }

    /**
     * Converte usuário em response detalhado.
     *
     * @param tenantUser entidade de usuário
     * @return DTO detalhado
     */
    public TenantUserDetailsResponse toDetails(TenantUser tenantUser) {
        validateUser(tenantUser);

        boolean enabled = calculateEnabled(tenantUser);
        TenantRole role = tenantUser.getRole();

        return new TenantUserDetailsResponse(
                tenantUser.getId(),
                tenantUser.getAccountId(),
                tenantUser.getName(),
                tenantUser.getEmail(),
                role,
                tenantUser.getPhone(),
                tenantUser.getAvatarUrl(),
                tenantUser.getTimezone(),
                tenantUser.getLocale(),
                tenantUser.isMustChangePassword(),
                tenantUser.getOrigin(),
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                tenantUser.isDeleted(),
                enabled
        );
    }

    /**
     * Converte usuário em item básico de listagem.
     *
     * @param tenantUser entidade de usuário
     * @return item básico
     */
    public TenantUserListItemResponse toListItemBasic(TenantUser tenantUser) {
        validateUser(tenantUser);

        boolean enabled = calculateEnabled(tenantUser);
        TenantRole role = tenantUser.getRole();

        return new TenantUserListItemResponse(
                tenantUser.getId(),
                tenantUser.getEmail(),
                role,
                List.of(),
                tenantUser.isMustChangePassword(),
                tenantUser.getOrigin(),
                null,
                null,
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                enabled
        );
    }

    /**
     * Converte usuário em item rico de listagem.
     *
     * @param tenantUser entidade de usuário
     * @return item rico
     */
    public TenantUserListItemResponse toListItemRich(TenantUser tenantUser) {
        validateUser(tenantUser);

        boolean enabled = calculateEnabled(tenantUser);
        List<String> permissions = mapPermissions(tenantUser.getPermissions());
        TenantActorRef createdBy = mapCreatedBy(tenantUser.getAudit());

        return new TenantUserListItemResponse(
                tenantUser.getId(),
                tenantUser.getEmail(),
                tenantUser.getRole(),
                permissions,
                tenantUser.isMustChangePassword(),
                tenantUser.getOrigin(),
                tenantUser.getLastLoginAt(),
                createdBy,
                tenantUser.isSuspendedByAccount(),
                tenantUser.isSuspendedByAdmin(),
                enabled
        );
    }

    /**
     * Mapeia metadados de criação para referência de ator.
     *
     * @param audit bloco de auditoria
     * @return ator criador ou {@code null}
     */
    private TenantActorRef mapCreatedBy(AuditInfo audit) {
        if (audit == null) {
            return null;
        }

        if (audit.getCreatedBy() == null && audit.getCreatedByEmail() == null) {
            return null;
        }

        return new TenantActorRef(audit.getCreatedBy(), audit.getCreatedByEmail());
    }

    /**
     * Converte permissões em lista ordenada de nomes.
     *
     * @param permissions permissões da entidade
     * @return lista ordenada e segura
     */
    private List<String> mapPermissions(Collection<TenantPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }

        return permissions.stream()
                .filter(Objects::nonNull)
                .map(TenantPermission::name)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * Calcula se o usuário está habilitado para uso.
     *
     * @param tenantUser entidade de usuário
     * @return {@code true} quando habilitado
     */
    private boolean calculateEnabled(TenantUser tenantUser) {
        return !tenantUser.isDeleted()
                && !tenantUser.isSuspendedByAccount()
                && !tenantUser.isSuspendedByAdmin();
    }

    /**
     * Valida entidade obrigatória para mapeamento.
     *
     * @param tenantUser entidade recebida
     */
    private void validateUser(TenantUser tenantUser) {
        if (tenantUser == null) {
            log.error("Tentativa de mapear TenantUser nulo na API");
            throw new IllegalArgumentException("tenantUser não pode ser nulo");
        }
    }
}