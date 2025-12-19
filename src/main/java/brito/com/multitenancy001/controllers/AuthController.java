package brito.com.multitenancy001.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.JwtResponse;
import brito.com.multitenancy001.dtos.TenantLoginRequest;
import brito.com.multitenancy001.entities.account.Account;
import brito.com.multitenancy001.entities.tenant.UserTenant;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.UserTenantRepository;
import brito.com.multitenancy001.security.JwtTokenProvider;
import brito.com.multitenancy001.services.UserTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final AccountRepository accountRepository;
    private final UserTenantRepository userTenantRepository;
    private final UserTenantService tenantUserService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> loginTenant(
            @Valid @RequestBody TenantLoginRequest request) {

        // 1️⃣ Resolve conta
        Account account = accountRepository
                .findBySlugAndDeletedFalse(request.slug())
                .orElseThrow(() -> new ApiException(
                        "ACCOUNT_NOT_FOUND",
                        "Conta não encontrada",
                        404
                ));

        if (!account.isActive()) {
            throw new ApiException(
                    "ACCOUNT_INACTIVE",
                    "Conta inativa",
                    403
            );
        }

        // 2️⃣ Define tenant
        TenantContext.setCurrentTenant(account.getSchemaName());

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.username(),
                                    request.password()
                            )
                    );

            UserTenant user = userTenantRepository
                    .findByUsernameAndAccountId(
                            request.username(),
                            account.getId()
                    )
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado",
                            404
                    ));

            if (!user.isActive() || user.isDeleted()) {
                throw new ApiException(
                        "USER_INACTIVE",
                        "Usuário inativo",
                        403
                );
            }

            String accessToken = tokenProvider.generateTenantToken(
                    authentication,
                    account.getId(),
                    account.getSchemaName()
            );

            String refreshToken = tokenProvider.generateRefreshToken(
                    user.getUsername(),
                    account.getSchemaName()
            );

            return ResponseEntity.ok(
                    new JwtResponse(
                            accessToken,
                            refreshToken,
                            user.getId(),
                            user.getUsername(),
                            user.getEmail(),
                            user.getRole().name(),
                            account.getId(),
                            account.getSchemaName()
                    )
            );

        } finally {
            TenantContext.clear();
        }
    }
    
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        String token = tenantUserService.generatePasswordResetToken(email);
        return ResponseEntity.ok("Token gerado: " + token);
    }  
    
    
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam String token,
                                                @RequestParam String newPassword) {
        tenantUserService.resetPasswordWithToken(token, newPassword);
        return ResponseEntity.ok("Senha redefinida com sucesso.");
    }
    
    
   
    
}
