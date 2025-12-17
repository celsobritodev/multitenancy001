package brito.com.multitenancy001.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.JwtResponse;
import brito.com.multitenancy001.dtos.LoginRequest;
import brito.com.multitenancy001.entities.account.Account;
import brito.com.multitenancy001.entities.account.UserAccount;
import brito.com.multitenancy001.entities.tenant.UserTenant;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.UserAccountRepository;
import brito.com.multitenancy001.repositories.UserTenantRepository;
import brito.com.multitenancy001.security.JwtTokenProvider;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserTenantRepository userTenantRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private AccountRepository accountRepository;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest) {
        
        // Verificar se é login account (slug especial)
        if (isAccountLogin(loginRequest.slug())) {
            return authenticateAccountUser(loginRequest);
        } else {
            return authenticateTenantUser(loginRequest);
        }
    }

    private boolean isAccountLogin(String slug) {
        // Login account quando slug é "account" ou começa com "account_"
        return "account".equalsIgnoreCase(slug) || 
               (slug != null && slug.startsWith("account_"));
    }

    private ResponseEntity<JwtResponse> authenticateAccountUser(LoginRequest loginRequest) {
        // Login no schema public (account)
        TenantContext.clear();
        
        try {
            // Buscar usuário account
            UserAccount user = userAccountRepository.findByUsername(loginRequest.username())
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário account não encontrado",
                            404
                    ));
            
            // Verificar se está ativo e não deletado
            if (!user.isActive() || user.isDeleted()) {
                throw new ApiException(
                        "USER_INACTIVE",
                        "Usuário inativo ou removido",
                        403
                );
            }
            
            // Verificar se é role de plataforma
            if (!user.getRole().isPlatformRole()) {
                throw new ApiException(
                        "INVALID_ROLE",
                        "Usuário não tem permissão para login Account",
                        403
                );
            }
            
            // Autenticar
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.username(),
                            loginRequest.password()
                    )
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Gerar tokens account
            String accessToken = tokenProvider.generateAccountToken(
                    authentication,
                    user.getAccount().getId(),
                    "public"
            );
            
            String refreshToken = tokenProvider.generateRefreshToken(
                    user.getUsername(),
                    "public"
            );
            
            return ResponseEntity.ok(new JwtResponse(
                    accessToken,
                    refreshToken,
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole().name(),
                    user.getAccount().getId(),
                    "public"
            ));
            
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    "INVALID_CREDENTIALS",
                    "Usuário ou senha inválidos",
                    401
            );
        } finally {
            TenantContext.clear();
        }
    }

    private ResponseEntity<JwtResponse> authenticateTenantUser(LoginRequest loginRequest) {
        // 1️⃣ Buscar conta
        Account account = accountRepository
                .findBySlugAndDeletedFalse(loginRequest.slug())
                .orElseThrow(() -> new ApiException(
                        "ACCOUNT_NOT_FOUND",
                        "Conta não encontrada",
                        404
                ));

        // Verificar se conta está ativa
        if (!account.isActive()) {
            throw new ApiException(
                    "ACCOUNT_INACTIVE",
                    "Conta inativa ou suspensa",
                    403
            );
        }

        // 2️⃣ Definir tenant
        TenantContext.setCurrentTenant(account.getSchemaName());

        try {
            // 3️⃣ Autenticar
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.username(),
                            loginRequest.password()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 4️⃣ Buscar usuário no tenant
            UserTenant user = userTenantRepository
                    .findByUsernameAndAccountId(loginRequest.username(), account.getId())
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado nesta conta",
                            404
                    ));

            // Verificar se usuário está ativo
            if (!user.isActive() || user.isDeleted()) {
                throw new ApiException(
                        "USER_INACTIVE",
                        "Usuário inativo ou removido",
                        403
                );
            }

            // 5️⃣ Gerar tokens
            String accessToken = tokenProvider.generateTenantToken(
                    authentication,
                    account.getId(),
                    account.getSchemaName()
            );

            String refreshToken = tokenProvider.generateRefreshToken(
                    user.getUsername(),
                    account.getSchemaName()
            );

            return ResponseEntity.ok(new JwtResponse(
                    accessToken,
                    refreshToken,
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole().name(),
                    account.getId(),
                    account.getSchemaName()
            ));

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    "INVALID_CREDENTIALS",
                    "Usuário ou senha inválidos",
                    401
            );
        } finally {
            TenantContext.clear(); // ⭐ SEMPRE LIMPAR
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshToken(
            @RequestBody Map<String, String> request) {

        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || !tokenProvider.validateToken(refreshToken)) {
            throw new ApiException(
                    "INVALID_REFRESH_TOKEN",
                    "Refresh token inválido ou expirado",
                    400
            );
        }

        String username = tokenProvider.getUsernameFromToken(refreshToken);
        String schema = tokenProvider.getTenantSchemaFromToken(refreshToken);

        TenantContext.setCurrentTenant(schema);

        try {
            // Determinar se é refresh de account ou tenant
            if ("public".equals(schema)) {
                // Refresh account
                UserAccount user = userAccountRepository
                        .findByUsernameAndDeletedFalse(username)
                        .orElseThrow(() -> new ApiException(
                                "USER_NOT_FOUND",
                                "Usuário account não encontrado",
                                404
                        ));

                // Verificar se está ativo
                if (!user.isActive()) {
                    throw new ApiException(
                            "USER_INACTIVE",
                            "Usuário account inativo",
                            403
                    );
                }

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(username);

                Authentication authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                String newAccessToken = tokenProvider.generateAccountToken(
                        authentication,
                        user.getAccount().getId(),
                        "public"
                );

                String newRefreshToken = tokenProvider.generateRefreshToken(
                        user.getUsername(),
                        "public"
                );

                return ResponseEntity.ok(Map.of(
                        "accessToken", newAccessToken,
                        "refreshToken", newRefreshToken
                ));
            } else {
                // Refresh tenant
                UserTenant user = userTenantRepository
                        .findByUsernameAndDeletedFalse(username)
                        .orElseThrow(() -> new ApiException(
                                "USER_NOT_FOUND",
                                "Usuário não encontrado",
                                404
                        ));

                // Verificar se está ativo
                if (!user.isActive()) {
                    throw new ApiException(
                            "USER_INACTIVE",
                            "Usuário inativo",
                            403
                    );
                }

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(username);

                Authentication authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                String newAccessToken = tokenProvider.generateTenantToken(
                        authentication,
                        user.getAccountId(),
                        schema
                );

                String newRefreshToken = tokenProvider.generateRefreshToken(
                        user.getUsername(),
                        schema
                );

                return ResponseEntity.ok(Map.of(
                        "accessToken", newAccessToken,
                        "refreshToken", newRefreshToken
                ));
            }

        } finally {
            TenantContext.clear(); // ⭐ OBRIGATÓRIO
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logoutUser() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logout realizado com sucesso"));
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        
        if (token == null) {
            throw new ApiException(
                    "TOKEN_REQUIRED",
                    "Token é obrigatório",
                    400
            );
        }
        
        boolean isValid = tokenProvider.validateToken(token);
        
        if (!isValid) {
            return ResponseEntity.status(401)
                    .body(Map.of("valid", false, "message", "Token inválido ou expirado"));
        }
        
        Map<String, Object> response = Map.of(
                "valid", true,
                "username", tokenProvider.getUsernameFromToken(token),
                "type", tokenProvider.getTokenType(token),
                "schema", tokenProvider.getTenantSchemaFromToken(token),
                "accountId", tokenProvider.getAccountIdFromToken(token),
                "userId", tokenProvider.getUserIdFromToken(token)
        );
        
        return ResponseEntity.ok(response);
    }
}