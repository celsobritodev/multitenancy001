package brito.com.multitenancy001.platform.application;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.multitenancy.SchemaContext;
import brito.com.multitenancy001.platform.api.dto.auth.PlatformAdminLoginRequest;
import brito.com.multitenancy001.platform.domain.user.PlatformUser;
import brito.com.multitenancy001.platform.persistence.publicdb.PlatformUserRepository;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final PlatformUserRepository platformUserRepository;

    public JwtResponse loginSuperAdmin(PlatformAdminLoginRequest request) {

        // 🔥 SUPER ADMIN SEMPRE NO PUBLIC
        SchemaContext.unbindSchema();

        PlatformUser user = platformUserRepository
                .findByUsernameAndDeletedFalse(request.username())
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Super admin não encontrado",
                        404
                ));

        // 🔒 Regras de negócio
        if (user.isSuspendedByAccount() || !user.getRole().isPlatformRole()) {
            throw new ApiException(
                    "ACCESS_DENIED",
                    "Usuário não autorizado",
                    403
            );
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        String accessToken = tokenProvider.generatePlatformToken(
                authentication,
                user.getAccount().getId(),
                "public"
        );

        String refreshToken = tokenProvider.generateRefreshToken(
                user.getUsername(),
                "public"
        );

        return new JwtResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getAccount().getId(),
                "public"
        );
    }
}
