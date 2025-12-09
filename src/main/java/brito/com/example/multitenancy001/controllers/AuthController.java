package brito.com.example.multitenancy001.controllers;

import java.util.HashMap;
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
        // CORREÇÃO: Use loginRequest.username() e loginRequest.password()
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.username(), // <-- CORREÇÃO AQUI
                loginRequest.password()  // <-- CORREÇÃO AQUI
            )
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // CORREÇÃO: Use loginRequest.username()
        User user = userRepository.findByUsername(loginRequest.username()) // <-- CORREÇÃO AQUI
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        String jwt = tokenProvider.generateToken(
            authentication, 
            user.getAccount().getId(), 
            user.getAccount().getSchemaName()
        );
        
        // CORREÇÃO: Use loginRequest.username()
        String refreshToken = tokenProvider.generateRefreshToken(loginRequest.username()); // <-- CORREÇÃO AQUI
        
        return ResponseEntity.ok(new JwtResponse(
            jwt,
            refreshToken,
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole().name(),
            user.getAccount().getId(),
            user.getAccount().getSchemaName()
        ));
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
}