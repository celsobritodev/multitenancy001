package brito.com.example.multitenancy001.services;


import brito.com.example.multitenancy001.dtos.UserCreateRequest;
import brito.com.example.multitenancy001.dtos.UserResponse;
import brito.com.example.multitenancy001.entities.master.Account;
import brito.com.example.multitenancy001.entities.master.User;
import brito.com.example.multitenancy001.entities.master.UserRole;
import brito.com.example.multitenancy001.repositories.AccountRepository;
import brito.com.example.multitenancy001.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
}