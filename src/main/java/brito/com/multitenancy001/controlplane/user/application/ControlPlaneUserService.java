package brito.com.multitenancy001.controlplane.user.application;

import brito.com.multitenancy001.controlplane.account.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.user.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.multitenancy.TenantSchemaContext;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private final ControlPlaneUserRepository platformUserRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    private Account getControlPlaneAccount() {
        TenantSchemaContext.clearTenantSchema(); // PUBLIC
        return accountRepository.findBySlugAndDeletedFalse("platform")
                .orElseThrow(() -> new ApiException(
                        "PLATFORM_ACCOUNT_NOT_FOUND",
                        "Conta platform não encontrada. Rode a migration V3__insert_platform_account.sql",
                        500
                ));
    }

    public ControlPlaneUserDetailsResponse createPlatformUser(ControlPlaneUserCreateRequest request) {
        TenantSchemaContext.clearTenantSchema();

        Account platformAccount = getControlPlaneAccount();

        // validações básicas
        if (!request.password().matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("INVALID_PASSWORD", "A senha não atende aos requisitos de segurança", 400);
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new ApiException("INVALID_USERNAME", "Username é obrigatório", 400);
        }
        if (!request.username().matches(ValidationPatterns.USERNAME_PATTERN)) {
            throw new ApiException("INVALID_USERNAME", "Username inválido", 400);
        }

        String username = request.username().toLowerCase().trim();

        // role permitida somente plataforma
        ControlPlaneRole role;
        try {
            role = ControlPlaneRole.valueOf(request.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("INVALID_ROLE", "Role inválida para plataforma", 400);
        }

        // garante que é role de plataforma (SUPER_ADMIN/SUPPORT/STAFF)
        if (!role.isPlatformRole()) {
            throw new ApiException("INVALID_ROLE", "Role não permitida para usuário de plataforma", 400);
        }

        // unicidade por account (platform)
        if (platformUserRepository.existsByUsernameAndAccountId(username, platformAccount.getId())) {
            throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe", 409);
        }
        if (platformUserRepository.existsByEmailAndAccountId(request.email(), platformAccount.getId())) {
            throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe", 409);
        }

        ControlPlaneUser user = ControlPlaneUser.builder()
                .name(request.name())
                .username(username)
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(role)
                .account(platformAccount) // ✅ SEMPRE PLATFORM
                .suspendedByAccount(false)
                .suspendedByAdmin(false)
                .createdAt(LocalDateTime.now())
                .build();

        return mapToResponse(platformUserRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<ControlPlaneUserDetailsResponse> listPlatformUsers() {
        TenantSchemaContext.clearTenantSchema();
        Account platformAccount = getControlPlaneAccount();
        return platformUserRepository.findByAccountId(platformAccount.getId()).stream()
                .filter(u -> !u.isDeleted())
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ControlPlaneUserDetailsResponse getPlatformUser(Long userId) {
        TenantSchemaContext.clearTenantSchema();
        Account platformAccount = getControlPlaneAccount();

        ControlPlaneUser user = platformUserRepository
                .findByIdAndAccountId(userId, platformAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404);
        }

        return mapToResponse(user);
    }
    
    
    
    
    
    
    public ControlPlaneUserDetailsResponse updatePlatformUserStatus(Long userId, boolean active) {
        TenantSchemaContext.clearTenantSchema();
        Account platformAccount = getControlPlaneAccount();

        ControlPlaneUser user = platformUserRepository
                .findByIdAndAccountId(userId, platformAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_DELETED", "Usuário está removido", 409);
        }

        // ✅ ação manual do admin da plataforma
        user.setSuspendedByAdmin(!active);
        user.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(platformUserRepository.save(user));
    }

    
    

    public void softDeletePlatformUser(Long userId) {
        TenantSchemaContext.clearTenantSchema();
        Account platformAccount = getControlPlaneAccount();

        ControlPlaneUser user = platformUserRepository
                .findByIdAndAccountId(userId, platformAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_ALREADY_DELETED", "Usuário já removido", 409);
        }

        user.softDelete();
        platformUserRepository.save(user);
    }
    

    public ControlPlaneUserDetailsResponse restorePlatformUser(Long userId) {
        TenantSchemaContext.clearTenantSchema();
        Account platformAccount = getControlPlaneAccount();

        ControlPlaneUser user = platformUserRepository
                .findByIdAndAccountId(userId, platformAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (!user.isDeleted()) {
            throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);
        }

        user.restore();
        return mapToResponse(platformUserRepository.save(user));
    }
    
    

    private ControlPlaneUserDetailsResponse mapToResponse(ControlPlaneUser user) {
        return new ControlPlaneUserDetailsResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getAccount().getId(),
                List.of()
        );
    }
}
