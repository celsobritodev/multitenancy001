package brito.com.multitenancy001.services;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.configuration.ValidationPatterns;
import brito.com.multitenancy001.dtos.UserCreateRequest;
import brito.com.multitenancy001.dtos.UserResponse;
import brito.com.multitenancy001.entities.account.Account;
import brito.com.multitenancy001.entities.account.UserAccount;
import brito.com.multitenancy001.entities.account.UserRole;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class UserAccountService {
    
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    
    public UserResponse createAccountUser(Long accountId, UserCreateRequest request) {
        // Garantir que estamos no schema public
        TenantContext.clear();
        
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(
                        "ACCOUNT_NOT_FOUND",
                        "Conta não encontrada",
                        404
                ));
   
        
        // Validação de senha
        if (!request.password().matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException(
                    "INVALID_PASSWORD",
                    "A senha não atende aos requisitos de segurança",
                    400
            );
        }
        
        // Validação de username
        if (request.username() != null && 
            !request.username().matches(ValidationPatterns.USERNAME_PATTERN)) {
            throw new ApiException(
                    "INVALID_USERNAME",
                    "Username inválido",
                    400
            );
        }
        
        // Normalizar username
        String username = request.username().toLowerCase().trim();
        
        // Verificar se username já existe
        if (userAccountRepository.existsByUsernameAndAccountId(username, accountId)) {
            throw new ApiException(
                    "USERNAME_ALREADY_EXISTS",
                    "Username já existe para esta conta",
                    409
            );
        }
        
        // Verificar se email já existe
        if (userAccountRepository.existsByEmailAndAccountId(request.email(), accountId)) {
            throw new ApiException(
                    "EMAIL_ALREADY_EXISTS",
                    "Email já cadastrado nesta conta",
                    409
            );
        }
        
        // Criar usuário account
        UserAccount user = UserAccount.builder()
                .name(request.name())
                .username(username)
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.valueOf(request.role().toUpperCase()))
                .account(account)
                .active(true)
         
                .createdAt(LocalDateTime.now())
                .build();
        
        UserAccount savedUser = userAccountRepository.save(user);
        return mapToResponse(savedUser);
    }
    
    public List<UserResponse> listAccountUsersByAccount(Long accountId) {
        TenantContext.clear();
        
        // Verificar se conta existe
        if (!accountRepository.existsById(accountId)) {
            throw new ApiException(
                "ACCOUNT_NOT_FOUND",
                "Conta não encontrada",
                404
            );
        }
        
        List<UserAccount> users = userAccountRepository.findByAccountId(accountId);
        return users.stream()
                .map(this::mapToResponse)
                .toList();
    }
    
    public UserResponse getAccountUser(Long userId) {
        TenantContext.clear();
        
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Usuário account não encontrado",
                        404
                ));
        
        return mapToResponse(user);
    }
    
    public UserResponse updateAccountUserStatus(Long userId, boolean active) {
        TenantContext.clear();
        
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Usuário não encontrado",
                        404
                ));
        
        user.setActive(active);
        user.setUpdatedAt(LocalDateTime.now());
        
        UserAccount updated = userAccountRepository.save(user);
        return mapToResponse(updated);
    }
    
    public void softDeleteAccountUser(Long userId) {
        TenantContext.clear();
        
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Usuário não encontrado",
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
        userAccountRepository.save(user);
    }
    
    public UserResponse restoreAccountUser(Long userId) {
        TenantContext.clear();
        
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Usuário não encontrado",
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
        UserAccount restored = userAccountRepository.save(user);
        return mapToResponse(restored);
    }
    
    public UserResponse resetAccountUserPassword(Long userId, String newPassword) {
        TenantContext.clear();
        
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Usuário não encontrado",
                        404
                ));
        
        if (user.isDeleted()) {
            throw new ApiException(
                    "USER_DELETED",
                    "Não é possível resetar a senha de um usuário removido",
                    400
            );
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(LocalDateTime.now());
        
        UserAccount updated = userAccountRepository.save(user);
        return mapToResponse(updated);
    }
    
    private UserResponse mapToResponse(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getAccount().getId(),
                List.of() // ✅ UserAccount NÃO TEM permissões
        );
    }
}