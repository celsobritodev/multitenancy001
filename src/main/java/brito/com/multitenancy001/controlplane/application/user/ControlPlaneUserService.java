package brito.com.multitenancy001.controlplane.application.user;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.security.PermissionNormalizer;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
   
    

    private Account getControlPlaneAccount() {
        TenantContext.clear(); // PUBLIC
        return accountRepository.findBySlugAndDeletedFalse("controlplane")
                .orElseThrow(() -> new ApiException(
                        "CONTROLPLANE_ACCOUNT_NOT_FOUND",
                        "Conta controlplane não encontrada. Rode a migration V3__insert_controlplane_account.sql",
                        500
                ));
    }

   public ControlPlaneUserDetailsResponse createControlPlaneUser(ControlPlaneUserCreateRequest request) {
    TenantContext.clear();

    Account controlPlaneAccount = getControlPlaneAccount();

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

   

    if (controlPlaneUserRepository.existsByUsernameAndAccountId(username, controlPlaneAccount.getId())) {
        throw new ApiException("USERNAME_ALREADY_EXISTS", "Username já existe", 409);
    }
    if (controlPlaneUserRepository.existsByEmailAndAccountId(request.email(), controlPlaneAccount.getId())) {
        throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já existe", 409);
    }

    // =========================================================
    // ✅ AQUI É O LOCAL EXATO: antes do builder
    // =========================================================
    Set<String> normalizedPermissions;
    try {
        normalizedPermissions = PermissionNormalizer.normalizeControlPlane(request.permissions());
    } catch (IllegalArgumentException e) {
        throw new ApiException("INVALID_PERMISSION", e.getMessage(), 400);
    }
    // =========================================================

    ControlPlaneUser user = ControlPlaneUser.builder()
            .name(request.name())
            .username(username)
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .role(role)
            .account(controlPlaneAccount)
            .suspendedByAccount(false)
            .suspendedByAdmin(false)

            // =====================================================
            // ✅ E AQUI: dentro do builder, antes do .build()
            // =====================================================
            .permissions(normalizedPermissions)

            // =====================================================

            .build();

    return mapToResponse(controlPlaneUserRepository.save(user));
}


    @Transactional(readOnly = true)
    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();
        return controlPlaneUserRepository.findByAccountId(controlPlaneAccount.getId()).stream()
                .filter(u -> !u.isDeleted())
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ControlPlaneUserDetailsResponse getControlPlaneUser(Long userId) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = controlPlaneUserRepository
                .findByIdAndAccountId(userId, controlPlaneAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404);
        }

        return mapToResponse(user);
    }
    
    
    
    
    
    
    public ControlPlaneUserDetailsResponse updateControlPlaneUserStatus(Long userId, boolean active) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = controlPlaneUserRepository
                .findByIdAndAccountId(userId, controlPlaneAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_DELETED", "Usuário está removido", 409);
        }

        // ✅ ação manual do admin da plataforma
        user.setSuspendedByAdmin(!active);

        return mapToResponse(controlPlaneUserRepository.save(user));
    }

    
    

    public void softDeleteControlPlaneUser(Long userId) {
    	   LocalDateTime now = LocalDateTime.now(clock);
           long suffix = clock.millis();
           
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = controlPlaneUserRepository
                .findByIdAndAccountId(userId, controlPlaneAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (user.isDeleted()) {
            throw new ApiException("USER_ALREADY_DELETED", "Usuário já removido", 409);
        }

     
        user.softDelete(now, suffix);
        controlPlaneUserRepository.save(user);

    }
    

    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        TenantContext.clear();
        Account controlPlaneAccount = getControlPlaneAccount();

        ControlPlaneUser user = controlPlaneUserRepository
                .findByIdAndAccountId(userId, controlPlaneAccount.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

        if (!user.isDeleted()) {
            throw new ApiException("USER_NOT_DELETED", "Usuário não está removido", 409);
        }

        user.restore();
        return mapToResponse(controlPlaneUserRepository.save(user));
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
            user.getPermissions() == null
                    ? List.of()
                    : user.getPermissions().stream().sorted().toList()
    );
}

}
