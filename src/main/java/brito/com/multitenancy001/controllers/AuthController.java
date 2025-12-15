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
import brito.com.multitenancy001.entities.master.Account;
import brito.com.multitenancy001.entities.master.User;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.UserRepository;
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
    private UserRepository userRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private AccountRepository accountRepository;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest) {

        // 1️⃣ Buscar conta
        Account account = accountRepository
                .findBySlugAndDeletedFalse(loginRequest.slug())
                .orElseThrow(() -> new ApiException(
                        "ACCOUNT_NOT_FOUND",
                        "Conta não encontrada",
                        404
                ));

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
            User user = userRepository
                    .findByUsernameAndAccountId(loginRequest.username(), account.getId())
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado nesta conta",
                            404
                    ));

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
            User user = userRepository
                    .findByUsernameAndDeletedFalse(username)
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado",
                            404
                    ));

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
                    user.getAccount().getId(),
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

        } finally {
            TenantContext.clear(); // ⭐ OBRIGATÓRIO
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logoutUser() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logout realizado com sucesso"));
    }
}
