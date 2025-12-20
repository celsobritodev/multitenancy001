package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.JwtResponse;
import brito.com.multitenancy001.dtos.SuperAdminLoginRequest;
import brito.com.multitenancy001.entities.account.UserAccount;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.UserAccountRepository;
import brito.com.multitenancy001.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserAccountRepository userAccountRepository;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> loginSuperAdmin(
            @Valid @RequestBody SuperAdminLoginRequest request) {

        // 🔥 SUPER ADMIN SEMPRE NO PUBLIC
        TenantContext.unbindTenant();

        UserAccount user = userAccountRepository
                .findByUsernameAndDeletedFalse(request.username())
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Super admin não encontrado",
                        404
                ));

        if (!user.isActive() || !user.getRole().isPlatformRole()) {
            throw new ApiException(
                    "ACCESS_DENIED",
                    "Usuário não autorizado",
                    403
            );
        }

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.username(),
                                request.password()
                        )
                );

        String accessToken = tokenProvider.generateAccountToken(
                authentication,
                user.getAccount().getId(),
                "public"
        );

        String refreshToken = tokenProvider.generateRefreshToken(
                user.getUsername(),
                "public"
        );

        return ResponseEntity.ok(
                new JwtResponse(
                        accessToken,
                        refreshToken,
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getRole().name(),
                        user.getAccount().getId(),
                        "public"
                )
        );
    }
}
