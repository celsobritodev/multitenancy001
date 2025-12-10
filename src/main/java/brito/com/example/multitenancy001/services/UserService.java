package brito.com.example.multitenancy001.services;


import brito.com.example.multitenancy001.dtos.UserCreateRequest;
import brito.com.example.multitenancy001.dtos.UserResponse;
import brito.com.example.multitenancy001.entities.master.Account;
import brito.com.example.multitenancy001.entities.master.User;
import brito.com.example.multitenancy001.entities.master.UserRole;
import brito.com.example.multitenancy001.exceptions.ApiException;
import brito.com.example.multitenancy001.repositories.AccountRepository;
import brito.com.example.multitenancy001.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    
    public UserResponse createUser(Long accountId, UserCreateRequest request) {
        // Buscar conta
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        
        // Verificar se email já existe na conta
        if (userRepository.findByEmailAndAccountId(request.email(), accountId).isPresent()) {
            throw new RuntimeException("Email já cadastrado nesta conta");
        }
        
        // Criar usuário
        User user = User.builder()
                .name(request.name())
                .username(generateUsername(request.email()))
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.valueOf(request.role().toUpperCase()))
                .account(account)
                .active(true)
                .permissions(request.permissions())
                .createdAt(LocalDateTime.now())
                .build();
        
        User savedUser = userRepository.save(user);
        
        return mapToResponse(savedUser);
    }
    
    private String generateUsername(String email) {
        String base = email.split("@")[0].toLowerCase();
        return base + "_" + UUID.randomUUID().toString().substring(0, 6);
    }
    
    private UserResponse mapToResponse(User user) {
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
                user.getPermissions()
        );
    }
    
    
    public List<UserResponse> listUsersByAccount(Long accountId) {

        // Verifica se a conta existe
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));

        // Busca os usuários da conta
        List<User> users = userRepository.findByAccountIdAndDeletedFalse(accountId);

        // Mapeia para DTO de resposta
        return users.stream()
                .map(this::mapToResponse)
                .toList();
    }
    
    
    public List<UserResponse> listActiveUsersByAccount(Long accountId) {

        // Verifica se a conta existe
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));

        // Busca usuários ativos (active = true, deleted = false)
        List<User> users = userRepository.findByAccountIdAndActiveTrueAndDeletedFalse(accountId);

        return users.stream()
                .map(this::mapToResponse)
                .toList();
    }
    
    
    public UserResponse updateUserStatus(Long accountId, Long userId, boolean active) {

        // Verifica se a conta existe
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));

        // Busca o usuário específico dentro da conta
        User user = userRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado nesta conta"));


        // Garante que o usuário pertence à conta informada
        if (!user.getAccount().getId().equals(accountId)) {
            throw new RuntimeException("Usuário não pertence a esta conta");
        }

        // Atualiza o status
        user.setActive(active);
        user.setUpdatedAt(LocalDateTime.now());

        User updated = userRepository.save(user);

        return mapToResponse(updated);
    }
    
    public void softDeleteUser(Long accountId, Long userId) {
        User user = userRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado nesta conta"));

        if (user.isDeleted()) {
            throw new RuntimeException("Usuário já está removido.");
        }

        user.softDelete();
        userRepository.save(user);
    }
    
    public UserResponse restoreUser(Long accountId, Long userId) {
        User user = userRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado nesta conta"));

        if (!user.isDeleted()) {
            throw new RuntimeException("Usuário não está removido.");
        }

        user.restore();
        User restored = userRepository.save(user);

        return mapToResponse(restored);
    }
    
    @Transactional
    public void hardDeleteUser(Long accountId, Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Usuário não encontrado",
                        404
                ));

        if (!user.getAccount().getId().equals(accountId)) {
            throw new ApiException(
                    "USER_NOT_IN_ACCOUNT",
                    "O usuário não pertence à conta especificada",
                    400
            );
        }

        // Bloqueia delete de usuários ainda ativos (segurança)
        if (!user.isDeleted()) {
            throw new ApiException(
                    "USER_NOT_SOFT_DELETED",
                    "Usuário deve ser soft-deletado antes de realizar hard delete",
                    409
            );
        }

        try {
            userRepository.delete(user); // 🔥 DELETE REAL
        } catch (Exception e) {
            throw new ApiException(
                    "DELETE_FAILED",
                    "Falha ao remover o usuário permanentemente",
                    500
            );
        }
    }


    public UserResponse resetPassword(Long accountId, Long userId, String newPassword) {

        User user = userRepository.findByIdAndAccountId(userId, accountId)
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

        // Define nova senha criptografada
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true); // força troca no próximo login
        user.setPasswordChangedAt(LocalDateTime.now());

        User updated = userRepository.save(user);
        return mapToResponse(updated);
    }

    
    
    public String generatePasswordResetToken(String email) {

        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new ApiException(
                        "EMAIL_NOT_FOUND",
                        "Nenhum usuário foi encontrado com este email",
                        404
                ));

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

        user.setPasswordResetToken(token);
        user.setPasswordResetExpires(expiresAt);

        userRepository.save(user);

        // Aqui você enviaria email real - por enquanto só log
        System.out.println("TOKEN DE RESET: " + token);
        
        return token;
    }

 
    public void resetPasswordWithToken(String token, String newPassword) {

        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new ApiException(
                        "INVALID_TOKEN",
                        "Token inválido ou expirado",
                        400
                ));

        if (user.getPasswordResetExpires().isBefore(LocalDateTime.now())) {
            throw new ApiException(
                    "TOKEN_EXPIRED",
                    "Token expirou, solicite novamente",
                    410
            );
        }

        user.changePassword(passwordEncoder.encode(newPassword));

        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);

        userRepository.save(user);
    }

  
    
    
    
}