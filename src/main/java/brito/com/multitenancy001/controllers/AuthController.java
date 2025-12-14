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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.JwtResponse;
import brito.com.multitenancy001.dtos.LoginRequest;
import brito.com.multitenancy001.entities.master.Account;
import brito.com.multitenancy001.entities.master.User;
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
    private UserDetailsService userDetailsService; // Adicione esta linha!
    
    @Autowired
    private AccountRepository accountRepository;
    
    
    
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        
        System.out.println("=== LOGIN DETALHADO ===");
        System.out.println("Username: " + loginRequest.username());
        
     // 1. Busca conta
        Account account = accountRepository
        	    .findBySlugAndDeletedFalse(loginRequest.slug())
        	    .orElseThrow(() -> new RuntimeException("Conta não encontrada"));

        
     // 2. Define tenant
        TenantContext.setCurrentTenant(account.getSchemaName());
        
        
        try {
            // 1. Tenta autenticar
            System.out.println("Tentando autenticar com AuthenticationManager...");
            
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.username(),
                    loginRequest.password()
                )
            );
            
            System.out.println("✅ Autenticação BEM-SUCEDIDA!");
            System.out.println("Principal: " + authentication.getPrincipal());
            System.out.println("Authorities: " + authentication.getAuthorities());
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // 4. Busca usuário dentro da conta
            User user = userRepository
                .findByUsernameAndAccountId(loginRequest.username(), account.getId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
            
         // 5. Gera token
            String jwt = tokenProvider.generateToken(
                authentication,
                account.getId(),
                account.getSchemaName()
            );

            return ResponseEntity.ok(new JwtResponse(
                jwt,
                tokenProvider.generateRefreshToken(user.getUsername(),account.getSchemaName()),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                account.getId(),
                account.getSchemaName()
            ));
            
        } catch (Exception e) {
            System.out.println("❌ Erro na autenticação!");
            System.out.println("Tipo: " + e.getClass().getName());
            System.out.println("Mensagem: " + e.getMessage());
            e.printStackTrace();
            
            return ResponseEntity.status(401).body("Login failed: " + e.getMessage());
        }
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {

        String refreshToken = request.get("refreshToken");

        if (!tokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.badRequest().body("Invalid refresh token");
        }

        String username = tokenProvider.getUsernameFromToken(refreshToken);
        String schema = tokenProvider.getTenantSchemaFromToken(refreshToken);

        // 1. Define tenant
        TenantContext.setCurrentTenant(schema);

        // 2. Busca usuário NO TENANT
        User user = userRepository
            .findByUsernameAndDeletedFalse(username)
            .orElseThrow(() -> new RuntimeException("User not found"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities()
        );

        String newJwt = tokenProvider.generateToken(
            authentication,
            user.getAccount().getId(),
            schema
        );

        String newRefreshToken = tokenProvider.generateRefreshToken(
        	    user.getUsername(),
        	    schema
        	);


        return ResponseEntity.ok(Map.of(
            "accessToken", newJwt,
            "refreshToken", newRefreshToken
        ));
    }

    
    
    
    
    
    
    
    
    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser() {
        // Em sistemas stateless com JWT, o logout é client-side
        // Você pode implementar uma blacklist de tokens se necessário
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logout successful");
    }
    
    
    
 
}