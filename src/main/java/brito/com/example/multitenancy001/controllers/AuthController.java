package brito.com.example.multitenancy001.controllers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import brito.com.example.multitenancy001.dtos.JwtResponse;
import brito.com.example.multitenancy001.dtos.LoginRequest;
import brito.com.example.multitenancy001.entities.master.User;
import brito.com.example.multitenancy001.repositories.UserRepository;
import brito.com.example.multitenancy001.security.JwtTokenProvider;
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
    
    //@Autowired
    //private PasswordEncoder passwordEncoder;
    
    @Autowired
    private UserDetailsService userDetailsService; // Adicione esta linha!
    
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        
        System.out.println("=== LOGIN DETALHADO ===");
        System.out.println("Username: " + loginRequest.username());
        
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
            
            // 2. Busca usuário
            User user = userRepository.findByUsername(loginRequest.username())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // 3. Gera token
            String jwt = tokenProvider.generateToken(
                authentication, 
                user.getAccount().getId(), 
                user.getAccount().getSchemaName()
            );
            
            System.out.println("✅ Token JWT gerado!");
            
            return ResponseEntity.ok(new JwtResponse(
                jwt,
                tokenProvider.generateRefreshToken(loginRequest.username()),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getAccount().getId(),
                user.getAccount().getSchemaName()
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
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Criar nova autenticação
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities()
        );
        
        String newJwt = tokenProvider.generateToken(
            authentication,
            user.getAccount().getId(),
            user.getAccount().getSchemaName()
        );
        
        String newRefreshToken = tokenProvider.generateRefreshToken(username);
        
        Map<String, String> response = new HashMap<>();
        response.put("accessToken", newJwt);
        response.put("refreshToken", newRefreshToken);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser() {
        // Em sistemas stateless com JWT, o logout é client-side
        // Você pode implementar uma blacklist de tokens se necessário
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logout successful");
    }
    
    
    
 // No AuthController:
    @PostMapping("/test-password-manual")
    public ResponseEntity<?> testPasswordManual(@RequestBody Map<String, String> data) {
        String username = data.get("username");
        String password = data.get("password");
        
        System.out.println("=== TESTE MANUAL DE SENHA ===");
        
        // 1. Busca o usuário
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Usuário não encontrado: " + username);
        }
        
        User user = userOpt.get();
        System.out.println("Usuário encontrado:");
        System.out.println("  ID: " + user.getId());
        System.out.println("  Username: " + user.getUsername());
        System.out.println("  Email: " + user.getEmail());
        System.out.println("  Active: " + user.isActive());
        System.out.println("  Password hash: " + user.getPassword());
        
        // 2. Testa a senha manualmente
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean matches = encoder.matches(password, user.getPassword());
        
        System.out.println("Senha testada: " + password);
        System.out.println("BCrypt matches: " + matches);
        
        // 3. Tenta criar autenticação manual
        if (matches) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                System.out.println("UserDetails carregado com sucesso!");
                
                Authentication auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
                
                System.out.println("Autenticação criada: " + auth.isAuthenticated());
                
                return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Senha CORRETA!",
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "active", user.isActive()
                ));
                
            } catch (Exception e) {
                System.out.println("Erro ao criar autenticação: " + e.getMessage());
                e.printStackTrace();
                return ResponseEntity.status(500).body("Erro na autenticação: " + e.getMessage());
            }
        } else {
            return ResponseEntity.status(401).body(Map.of(
                "status", "FAIL",
                "message", "Senha INCORRETA!",
                "userId", user.getId()
            ));
        }
    }
}