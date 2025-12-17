package brito.com.multitenancy001.services;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.configuration.ValidationPatterns;
import brito.com.multitenancy001.dtos.UserCreateRequest;
import brito.com.multitenancy001.dtos.UserResponse;
import brito.com.multitenancy001.entities.account.Account;
import brito.com.multitenancy001.entities.account.UserRole;
import brito.com.multitenancy001.entities.tenant.UserTenant;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.UserTenantRepository;
import brito.com.multitenancy001.security.JwtTokenProvider;
import brito.com.multitenancy001.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UserTenantService {
    
    private final UserTenantRepository userTenantRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGeneratorService usernameGenerator;
    private final UsernameUniquenessService usernameUniquenessService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityUtils securityUtils;
    
   public UserResponse createTenantUser(UserCreateRequest request) {

    Long accountId = securityUtils.getCurrentAccountId();
    String schemaName = securityUtils.getCurrentSchema();

    Account account;

    try {
        // 🔑 1. FORÇA ACCOUNT (public)
        TenantContext.clear();
        account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ApiException(
                "ACCOUNT_NOT_FOUND",
                "Conta não encontrada",
                404
            ));
    } finally {
        TenantContext.clear();
    }

    if (!account.isActive()) {
        throw new ApiException(
            "ACCOUNT_INACTIVE",
            "Conta inativa ou suspensa",
            403
        );
    }

    // 🔁 2. ENTRA NO TENANT
    TenantContext.setCurrentTenant(schemaName);

    try {
        if (hasReachedUserLimit(account)) {
            throw new ApiException(
                "USER_LIMIT_REACHED",
                "Limite máximo de usuários atingido para esta conta",
                403
            );
        }

        validateUserCreateRequest(request);

        if (userTenantRepository.existsByEmailAndAccountId(request.email(), accountId)) {
            throw new ApiException(
                "EMAIL_ALREADY_EXISTS",
                "Email já cadastrado nesta conta",
                409
            );
        }

        String username = generateUsername(request, accountId, schemaName);

        validateUserRole(request.role());

        UserTenant user = buildUserTenant(request, username, accountId);
        UserTenant savedUser = userTenantRepository.save(user);

        return mapToResponse(savedUser);

    } finally {
        TenantContext.clear();
    }
}

    
   private boolean hasReachedUserLimit(Account account) {
    long activeUsersCount = userTenantRepository
        .findByAccountId(account.getId())
        .stream()
        .filter(u -> u.isActive() && !u.isDeleted())
        .count();

    return activeUsersCount >= account.getMaxUsers();
}

    
    private void validateUserCreateRequest(UserCreateRequest request) {
        // Validação de senha
        if (!request.password().matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException(
                    "INVALID_PASSWORD",
                    "A senha não atende aos requisitos de segurança",
                    400
            );
        }
        
        // Validação de username (se fornecido)
        if (request.username() != null && 
            !request.username().matches(ValidationPatterns.USERNAME_PATTERN)) {
            throw new ApiException(
                    "INVALID_USERNAME",
                    "Username inválido",
                    400
            );
        }
        
        // Validação de telefone (se fornecido)
        if (request.phone() != null && !request.phone().isEmpty() &&
            !request.phone().matches(ValidationPatterns.PHONE_PATTERN)) {
            throw new ApiException(
                    "INVALID_PHONE",
                    "Telefone inválido",
                    400
            );
        }
    }
    
    private String generateUsername(UserCreateRequest request, Long accountId,String schemaName ) {
    	
        if (request.username() != null && !request.username().trim().isEmpty()) {
            String username = request.username().trim().toLowerCase();
            
            if (userTenantRepository.existsByUsernameAndAccountId(username, accountId)) {
                throw new ApiException(
                        "USERNAME_ALREADY_EXISTS",
                        "Username já está em uso nesta conta",
                        409
                );
            }
            return username;
        } else {
        	
            // Gerar username automaticamente do email
            String username = usernameGenerator.generateFromEmail(request.email());
            return usernameUniquenessService
                    .ensureUniqueUsername(username, accountId);

        }
    }
    
    private void validateUserRole(String role) {
        try {
            UserRole userRole = UserRole.valueOf(role.toUpperCase());
            
            // Verificar se a role é válida para tenant (não pode ser SUPER_ADMIN)
            if (userRole == UserRole.SUPER_ADMIN) {
                throw new ApiException(
                        "INVALID_ROLE_FOR_TENANT",
                        "SUPER_ADMIN não é uma role válida para usuários de tenant",
                        400
                );
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    "INVALID_ROLE",
                    "Role inválida: " + role,
                    400
            );
        }
    }
    
    private UserTenant buildUserTenant(UserCreateRequest request, String username, Long accountId) {
        return UserTenant.builder()
                .name(request.name())
                .username(username)
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.valueOf(request.role().toUpperCase()))
                .accountId(accountId)
                .active(true)
                .permissions(request.permissions() != null ? request.permissions() : List.of())
                .avatarUrl(request.avatarUrl() != null ? request.avatarUrl().trim() : null)
                .phone(request.phone() != null ? request.phone().trim() : null)
                .createdAt(LocalDateTime.now())
                .createdBy(securityUtils.getCurrentUserId())
                .build();
    }
    
    public List<UserResponse> listTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schemaName = securityUtils.getCurrentSchema();
        
        TenantContext.setCurrentTenant(schemaName);
        
        try {
            List<UserTenant> users = userTenantRepository.findByAccountIdAndDeletedFalse(accountId);
            return users.stream()
                    .map(this::mapToResponse)
                    .toList();
        } finally {
            TenantContext.clear();
        }
    }
    
    public List<UserResponse> listActiveTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String schemaName = securityUtils.getCurrentSchema();
        
        TenantContext.setCurrentTenant(schemaName);
        
        try {
            List<UserTenant> users = userTenantRepository.findByAccountId(accountId)
                    .stream()
                    .filter(user -> user.isActive() && !user.isDeleted())
                    .toList();
            return users.stream()
                    .map(this::mapToResponse)
                    .toList();
        } finally {
            TenantContext.clear();
        }
    }
    
    public UserResponse getTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schemaName = securityUtils.getCurrentSchema();
        
        TenantContext.setCurrentTenant(schemaName);
        
        try {
            UserTenant user = userTenantRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado nesta conta",
                            404
                    ));
            
            return mapToResponse(user);
        } finally {
            TenantContext.clear();
        }
    }
    
    public UserResponse updateTenantUserStatus(Long userId, boolean active) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schemaName = securityUtils.getCurrentSchema();
        
        TenantContext.setCurrentTenant(schemaName);
        
        try {
            UserTenant user = userTenantRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado nesta conta",
                            404
                    ));
            
            user.setActive(active);
            user.setUpdatedAt(LocalDateTime.now());
            user.setUpdatedBy(securityUtils.getCurrentUserId());
            
            UserTenant updated = userTenantRepository.save(user);
            return mapToResponse(updated);
        } finally {
            TenantContext.clear();
        }
    }
    
    public void softDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schemaName = securityUtils.getCurrentSchema();
        
        TenantContext.setCurrentTenant(schemaName);
        
        try {
            UserTenant user = userTenantRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado nesta conta",
                            404
                    ));
            
            if (user.isDeleted()) {
                throw new ApiException(
                        "USER_ALREADY_DELETED",
                        "Usuário já está removido",
                        409
                );
            }
            
            user.softDelete();
            userTenantRepository.save(user);
            
            log.info("Usuário tenant soft deletado: {} na conta {}", 
                    user.getUsername(), accountId);
        } finally {
            TenantContext.clear();
        }
    }
    
    public UserResponse restoreTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schemaName = securityUtils.getCurrentSchema();
        
        TenantContext.setCurrentTenant(schemaName);
        
        try {
            UserTenant user = userTenantRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado nesta conta",
                            404
                    ));
            
            if (!user.isDeleted()) {
                throw new ApiException(
                        "USER_NOT_DELETED",
                        "O usuário não está removido e não pode ser restaurado",
                        409
                );
            }
            
            user.restore();
            UserTenant restored = userTenantRepository.save(user);
            
            log.info("Usuário tenant restaurado: {} na conta {}", 
                    restored.getUsername(), accountId);
            
            return mapToResponse(restored);
        } finally {
            TenantContext.clear();
        }
    }
    
    public UserResponse resetTenantUserPassword(Long userId, String newPassword) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schemaName = securityUtils.getCurrentSchema();
        
        TenantContext.setCurrentTenant(schemaName);
        
        try {
            UserTenant user = userTenantRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado nesta conta",
                            404
                    ));
            
            if (user.isDeleted()) {
                throw new ApiException(
                        "USER_DELETED",
                        "Não é possível resetar a senha de um usuário removido",
                        400
                );
            }
            
            // Validar nova senha
            if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
                throw new ApiException(
                        "INVALID_PASSWORD",
                        "A nova senha não atende aos requisitos de segurança",
                        400
                );
            }
            
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(true);
            user.setPasswordChangedAt(LocalDateTime.now());
            
            UserTenant updated = userTenantRepository.save(user);
            
            log.info("Senha resetada para usuário tenant: {} na conta {}", 
                    updated.getUsername(), accountId);
            
            return mapToResponse(updated);
        } finally {
            TenantContext.clear();
        }
    }
    
    public void hardDeleteTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schemaName = securityUtils.getCurrentSchema();
        
        TenantContext.setCurrentTenant(schemaName);
        
        try {
            UserTenant user = userTenantRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado",
                            404
                    ));
            
            if (!user.getAccountId().equals(accountId)) {
                throw new ApiException(
                        "USER_NOT_IN_ACCOUNT",
                        "O usuário não pertence à conta especificada",
                        400
                );
            }
            
            if (!user.isDeleted()) {
                throw new ApiException(
                        "USER_NOT_SOFT_DELETED",
                        "Usuário deve ser soft-deletado antes de realizar hard delete",
                        409
                );
            }
            
            userTenantRepository.delete(user);
            
            log.warn("Usuário tenant hard deletado: {} (ID: {}) na conta {}", 
                    user.getUsername(), userId, accountId);
        } finally {
            TenantContext.clear();
        }
    }
    
    public String generatePasswordResetToken(String email) {
        // Esta implementação busca apenas no tenant atual
        // Para uma solução completa, você precisaria de uma tabela de lookup no ACCOUNT
        
        Long accountId = securityUtils.getCurrentAccountId();
        String schemaName = securityUtils.getCurrentSchema();
        
        TenantContext.setCurrentTenant(schemaName);
        
        try {
            UserTenant user = userTenantRepository.findByEmailAndDeletedFalse(email)
                    .orElseThrow(() -> new ApiException(
                            "EMAIL_NOT_FOUND",
                            "Nenhum usuário encontrado com este email",
                            404
                    ));
            
            // Verificar se o usuário está ativo
            if (!user.isActive()) {
                throw new ApiException(
                        "USER_INACTIVE",
                        "Usuário inativo",
                        403
                );
            }
            
            // Gerar token
            String token = jwtTokenProvider.generatePasswordResetToken(
                    user.getUsername(),
                    schemaName,
                    accountId
            );
            
            log.info("Token de reset de senha gerado para: {} na conta {}", 
                    user.getEmail(), accountId);
            
            return token;
        } finally {
            TenantContext.clear();
        }
    }
    
    @Transactional
    public void resetPasswordWithToken(String token, String newPassword) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new ApiException(
                    "INVALID_TOKEN",
                    "Token inválido ou expirado",
                    400
            );
        }
        
        // Verificar se é um token de password reset
        if (!jwtTokenProvider.isPasswordResetToken(token)) {
            throw new ApiException(
                    "INVALID_TOKEN_TYPE",
                    "Tipo de token inválido",
                    400
            );
        }
        
        String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(token);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String username = jwtTokenProvider.getUsernameFromToken(token);
        
        // Validar nova senha
        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException(
                    "INVALID_PASSWORD",
                    "A nova senha não atende aos requisitos de segurança",
                    400
            );
        }
        
        TenantContext.setCurrentTenant(tenantSchema);
        
        try {
            UserTenant user = userTenantRepository
                    .findByUsernameAndAccountId(username, accountId)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado",
                            404
                    ));
            
            if (user.isDeleted()) {
                throw new ApiException(
                        "USER_DELETED",
                        "Usuário removido",
                        400
                );
            }
            
            if (!user.isActive()) {
                throw new ApiException(
                        "USER_INACTIVE",
                        "Usuário inativo",
                        403
                );
            }
            
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordChangedAt(LocalDateTime.now());
            user.setMustChangePassword(false);
            
            userTenantRepository.save(user);
            
            log.info("Senha redefinida via token para usuário: {} na conta {}", 
                    user.getUsername(), accountId);
        } finally {
            TenantContext.clear();
        }
    }
    
    public boolean checkCredentials(String slug, String username, String rawPassword) {
        Account account = accountRepository
                .findBySlugAndDeletedFalse(slug)
                .orElseThrow(() -> new ApiException(
                        "ACCOUNT_NOT_FOUND",
                        "Conta não encontrada para o identificador informado",
                        404
                ));
        
        // 🔹 USANDO A VARIÁVEL ACCOUNT
        // Verificar se a conta está ativa
        if (!account.isActive()) {
            throw new ApiException(
                    "ACCOUNT_INACTIVE",
                    "Conta inativa ou suspensa",
                    403
            );
        }
        
        TenantContext.setCurrentTenant(account.getSchemaName());
        
        try {
            UserTenant user = userTenantRepository
                    .findByUsernameAndAccountId(username, account.getId())
                    .orElse(null);
            
            if (user == null) {
                return false;
            }
            
            // Verificar se o usuário está ativo e não deletado
            if (!user.isActive() || user.isDeleted()) {
                return false;
            }
            
            return passwordEncoder.matches(rawPassword, user.getPassword());
        } finally {
            TenantContext.clear();
        }
    }
    
    // Método para atualizar informações do usuário
    public UserResponse updateTenantUser(Long userId, UserCreateRequest request) {
        Long accountId = securityUtils.getCurrentAccountId();
        String schemaName = securityUtils.getCurrentSchema();
        
        TenantContext.setCurrentTenant(schemaName);
        
        try {
            UserTenant user = userTenantRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado nesta conta",
                            404
                    ));
            
            if (user.isDeleted()) {
                throw new ApiException(
                        "USER_DELETED",
                        "Não é possível atualizar um usuário removido",
                        400
                );
            }
            
            // Atualizar campos permitidos
            if (request.name() != null && !request.name().isBlank()) {
                user.setName(request.name());
            }
            
            if (request.email() != null && !request.email().isBlank() && 
                !request.email().equals(user.getEmail())) {
                // Verificar se novo email já existe
                if (userTenantRepository.existsByEmailAndAccountIdAndIdNot(
                        request.email(), accountId, userId)) {
                    throw new ApiException(
                            "EMAIL_ALREADY_EXISTS",
                            "Email já cadastrado nesta conta",
                            409
                    );
                }
                user.setEmail(request.email());
            }
            
            if (request.phone() != null && !request.phone().isBlank()) {
                user.setPhone(request.phone().trim());
            }
            
            if (request.avatarUrl() != null && !request.avatarUrl().isBlank()) {
                user.setAvatarUrl(request.avatarUrl().trim());
            }
            
            if (request.permissions() != null) {
                user.setPermissions(request.permissions());
            }
            
            user.setUpdatedAt(LocalDateTime.now());
            user.setUpdatedBy(securityUtils.getCurrentUserId());
            
            UserTenant updated = userTenantRepository.save(user);
            
            log.info("Usuário tenant atualizado: {} na conta {}", 
                    updated.getUsername(), accountId);
            
            return mapToResponse(updated);
        } finally {
            TenantContext.clear();
        }
    }
    
    private UserResponse mapToResponse(UserTenant user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getAccountId(),
                user.getPermissions()
        );
    }
}