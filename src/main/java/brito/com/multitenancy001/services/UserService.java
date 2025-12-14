package brito.com.multitenancy001.services;


import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.configuration.ValidationPatterns;
import brito.com.multitenancy001.dtos.UserCreateRequest;
import brito.com.multitenancy001.dtos.UserResponse;
import brito.com.multitenancy001.entities.master.Account;
import brito.com.multitenancy001.entities.master.User;
import brito.com.multitenancy001.entities.master.UserRole;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.UserRepository;
import brito.com.multitenancy001.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGeneratorService usernameGenerator; // ✅ Injetado
    
    private final UsernameUniquenessService usernameUniquenessService;
    
    private final JwtTokenProvider jwtTokenProvider;
    
    
    public UserResponse createUser(Long accountId, UserCreateRequest request) {
    	
    	// Validação adicional no service
        if (!request.password().matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException(
                "INVALID_PASSWORD",
                "A senha não atende aos requisitos de segurança",
                400
            );
        }
    	
    	
     // Valida username se fornecido
        if (request.username() != null && 
            !request.username().matches(ValidationPatterns.USERNAME_PATTERN)) {
            throw new ApiException(
                "INVALID_USERNAME",
                "Username inválido.",
                400
            );
        }
    	
        // Buscar conta
    	Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(
                    "ACCOUNT_NOT_FOUND",
                    "Conta não encontrada",
                    404
                ));
        
        // Verificar se email já existe na conta
    	if (userRepository.findByEmailAndAccountId(request.email(), accountId).isPresent()) {
            throw new ApiException(
                "EMAIL_ALREADY_EXISTS",
                "Email já cadastrado nesta conta",
                409
            );
        }
        
     // ✅ Usar o service centralizado
        String username;
        if (request.username() != null && !request.username().trim().isEmpty()) {
            username = request.username().trim().toLowerCase();
            
            // Verificar se username já existe
            if (userRepository.existsByUsernameAndAccountId(username, accountId)) {
                throw new RuntimeException("Username já está em uso nesta conta");
            }
        } else {
            // Gerar username automaticamente
            username = usernameGenerator.generateFromEmail(request.email());
            
            // Garantir unicidade
            username = usernameUniquenessService.ensureUniqueUsername(username, accountId);
        }
        
        
        System.out.println("🔍 Email: " + request.email());
        System.out.println("🔍 Username gerado: " + username);
        System.out.println("🔍 Pattern atual: " + ValidationPatterns.USERNAME_PATTERN);
        System.out.println("🔍 Válido? " + username.matches(ValidationPatterns.USERNAME_PATTERN));
        
        
        
        // Criar usuário
        User user = User.builder()
                .name(request.name())
                .username(username)  // 👈 AQUI
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

    	// ✅ Verifica se conta existe (forma eficiente)
        if (!accountRepository.existsById(accountId)) {
            throw new ApiException(
                "ACCOUNT_NOT_FOUND",
                "Conta não encontrada",
                404
            );
        }

        // Busca os usuários da conta
        List<User> users = userRepository.findByAccountIdAndDeletedFalse(accountId);

        // Mapeia para DTO de resposta
        return users.stream()
                .map(this::mapToResponse)
                .toList();
    }
    
    
    public List<UserResponse> listActiveUsersByAccount(Long accountId) {

    	 // ✅ Verifica se conta existe
        if (!accountRepository.existsById(accountId)) {
            throw new ApiException(
                "ACCOUNT_NOT_FOUND",
                "Conta não encontrada",
                404
            );
        }

        // Busca usuários ativos (active = true, deleted = false)
        List<User> users = userRepository.findByAccountIdAndActiveTrueAndDeletedFalse(accountId);

        return users.stream()
                .map(this::mapToResponse)
                .toList();
    }
    
    
    public UserResponse updateUserStatus(Long accountId, Long userId, boolean active) {

    	// ✅ Busca o usuário (já valida conta através do repositório)
        User user = userRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException(
                    "USER_NOT_FOUND_IN_ACCOUNT",
                    "Usuário não encontrado nesta conta",
                    404
                ));
        
      
        // Atualiza o status
        user.setActive(active);
        user.setUpdatedAt(LocalDateTime.now());

        User updated = userRepository.save(user);

        return mapToResponse(updated);
    }
    
    public void softDeleteUser(Long accountId, Long userId) {
    	User user = userRepository.findByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException(
                    "USER_NOT_FOUND_IN_ACCOUNT",
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

    	// 1️⃣ Busca no MASTER (public)
        TenantContext.clear(); // remove o tenant atual
    	
    	User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new ApiException(
                        "EMAIL_NOT_FOUND",
                        "Nenhum usuário foi encontrado com este email",
                        404
                ));
    	
    	Account account = user.getAccount();
    	
        String token = jwtTokenProvider.generatePasswordResetToken(
                user.getUsername(),
                account.getSchemaName(),   // ⭐ AQUI
                account.getId()); 
        
        return token;
    }
    
    

 
    @Transactional
    public void resetPasswordWithToken(String token, String newPassword) {

        // 1️⃣ Validar JWT
        if (!jwtTokenProvider.validateToken(token)) {
            throw new ApiException(
                    "INVALID_TOKEN",
                    "Token inválido ou expirado",
                    400
            );
        }

        // 2️⃣ Extrair dados do token
        String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(token);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String username = jwtTokenProvider.getUsernameFromToken(token);

        // 3️⃣ SETAR O TENANT (⭐ PONTO QUE VOCÊ PERGUNTOU ⭐)
        TenantContext.setCurrentTenant(tenantSchema);

        try {
            // 4️⃣ Buscar usuário NO SCHEMA CORRETO
            User user = userRepository
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

            // 5️⃣ Alterar senha
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordChangedAt(LocalDateTime.now());
            user.setMustChangePassword(false);

            userRepository.save(user);

        } finally {
            // 6️⃣ LIMPAR CONTEXTO (OBRIGATÓRIO)
            TenantContext.clear();
        }
    }

    
    
    
    
    
    
    
    public boolean checkCredentials(String slug, String username, String rawPassword) {

        Account account = accountRepository
                .findBySlugAndDeletedFalse(slug)
                .orElseThrow(() -> new ApiException(
                        "ACCOUNT_NOT_FOUND",
                        "Conta não encontrada",
                        404
                ));

        TenantContext.setCurrentTenant(account.getSchemaName());

        try {
            User user = userRepository
                    .findByUsernameAndDeletedFalse(username)
                    .orElse(null);

            if (user == null) {
                return false;
            }

            return passwordEncoder.matches(rawPassword, user.getPassword());
        } finally {
            TenantContext.clear();
        }
    }


  

    
}