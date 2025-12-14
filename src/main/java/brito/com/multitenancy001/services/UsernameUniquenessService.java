package brito.com.multitenancy001.services;

import java.util.UUID;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.repositories.UserRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UsernameUniquenessService {
    
    private final UserRepository userRepository;
    
    public String ensureUniqueUsername(String baseUsername, Long accountId) {
        String username = baseUsername;
        int counter = 1;
        
        while (userRepository.existsByUsernameAndAccountId(username, accountId)) {
            username = baseUsername + "_" + counter;
            counter++;
            
            // Prevenir loop infinito
            if (counter > 100) {
                username = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 8);
                break;
            }
        }
        
        return username;
    }
    
    public boolean isUsernameAvailable(String username, Long accountId) {
        return !userRepository.existsByUsernameAndAccountId(username, accountId);
    }
}