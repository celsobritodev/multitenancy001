package brito.com.multitenancy001.platform.application;

import brito.com.multitenancy001.infrastructure.multitenancy.SchemaContext;
import brito.com.multitenancy001.platform.api.dto.users.PlatformUserCreateRequest;
import brito.com.multitenancy001.platform.api.dto.users.PlatformUserDetailsResponse;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.platform.domain.user.PlatformRole;
import brito.com.multitenancy001.platform.domain.user.PlatformUser;
import brito.com.multitenancy001.platform.persistence.publicdb.AccountRepository;
import brito.com.multitenancy001.platform.persistence.publicdb.PlatformUserRepository;
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
public class PlatformUserService {

    private final PlatformUserRepository platformUserRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    private TenantAccount getPlatformAccount() {
        SchemaContext.unbindSchema(); // PUBLIC
        return accountRepository.findBySlugAndDeletedFalse("platform")
                .orElseThrow(() -> new ApiException(
                        "PLATFORM_ACCOUNT_NOT_FOUND",
                        "Conta platform não encontrada. Rode a migration V3__insert_platform_account.sql",
                        500
                ));
    }

    public PlatformUserDetailsResponse createPlatformUser(PlatformUserCreateRequest request) {
        SchemaContext.unbindSchema();

        TenantAccount platformAccount = getPlatformAccount();

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
        PlatformRole role;
        try {
            role = PlatformRole.valueOf(request.role().toUpperCase());
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

        PlatformUser user = PlatformUser.builder()
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
    public List<PlatformUserDetailsResponse> listPlatformUsers() {
        SchemaContext.unbindSchema();
        TenantAccount platformAccount = getPlatformAccount();
        return platformUserRepository.findByAccountId(platformAccount.getId()).stream()
                .filter(u -> !u.isDeleted())
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformUserDetailsResponse getPlatformUser(Long userId) {
        SchemaContext.unbindSchema();
        TenantAccount platformAccount = getPlatformAccount();

        PlatformUser user = platformUserRepository
                .findByIdAndAccountId(userId, platformAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404);
        }

        return mapToResponse(user);
    }
    
    
    
    
    
    
    public PlatformUserDetailsResponse updatePlatformUserStatus(Long userId, boolean active) {
        SchemaContext.unbindSchema();
        TenantAccount platformAccount = getPlatformAccount();

        PlatformUser user = platformUserRepository
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
        SchemaContext.unbindSchema();
        TenantAccount platformAccount = getPlatformAccount();

        PlatformUser user = platformUserRepository
                .findByIdAndAccountId(userId, platformAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_ALREADY_DELETED", "Usuário já removido", 409);
        }

        user.softDelete();
        platformUserRepository.save(user);
    }
    

    public PlatformUserDetailsResponse restorePlatformUser(Long userId) {
        SchemaContext.unbindSchema();
        TenantAccount platformAccount = getPlatformAccount();

        PlatformUser user = platformUserRepository
                .findByIdAndAccountId(userId, platformAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (!user.isDeleted()) {
            throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);
        }

        user.restore();
        return mapToResponse(platformUserRepository.save(user));
    }
    
    

    private PlatformUserDetailsResponse mapToResponse(PlatformUser user) {
        return new PlatformUserDetailsResponse(
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
