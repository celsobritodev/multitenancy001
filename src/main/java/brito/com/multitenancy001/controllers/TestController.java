package brito.com.multitenancy001.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import brito.com.multitenancy001.services.AccountService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {
    
    private final AccountService accountService;
    
    
    @PostMapping("/tenant-flow")
    public ResponseEntity<?> testTenantFlow() {
        try {
            // 1. Testa criação simples
            boolean schemaReady = accountService.testTenantCreation("test_schema_123");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "schemaReady", schemaReady,
                "message", "Flujo de tenant testado com sucesso"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}