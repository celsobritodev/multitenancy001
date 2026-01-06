package brito.com.multitenancy001.platform.api.dto.accounts;

import brito.com.multitenancy001.platform.api.dto.users.PlatformAdminUserSummaryResponse;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.platform.domain.user.PlatformUser;

import java.time.LocalDateTime;

public record AccountResponse(
    Long id,
    String name,
    String slug,
    String schemaName,
    String status,
    LocalDateTime createdAt,
    LocalDateTime trialEndDate,
    PlatformAdminUserSummaryResponse admin,
    boolean systemAccount
) {
    
    // Método estático para criar a partir da entidade (sem admin)
    public static AccountResponse fromEntity(TenantAccount account) {
        return new AccountResponse(
            account.getId(),
            account.getName(),
            account.getSlug(),
            account.getSchemaName(),
            account.getStatus().name(),
            account.getCreatedAt(),
            account.getTrialEndDate(),
            null, // admin só vem quando cria conta
            account.isSystemAccount()
        );
    }
    
    // Método estático para criar a partir da entidade (com admin)
    public static AccountResponse fromEntity(TenantAccount account, PlatformUser userAccount) {
    	PlatformAdminUserSummaryResponse adminResponse = userAccount != null
    	        ? PlatformAdminUserSummaryResponse.from(userAccount)
    	        : null;
            
        return new AccountResponse(
            account.getId(),
            account.getName(),
            account.getSlug(),
            account.getSchemaName(),
            account.getStatus().name(),
            account.getCreatedAt(),
            account.getTrialEndDate(),
            adminResponse,
            account.isSystemAccount()
        );
    }
    
    // Método para criar com builder pattern (opcional)
    public static Builder builder() {
        return new Builder();
    }
    
    // Builder para facilitar a construção
    public static class Builder {
        private Long id;
        private String name;
        private String slug;
        private String schemaName;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime trialEndDate;
        private PlatformAdminUserSummaryResponse admin;
        private boolean systemAccount;
        
        public Builder id(Long id) { 
            this.id = id; 
            return this; 
        }
        
        public Builder name(String name) { 
            this.name = name; 
            return this; 
        }
        
        public Builder slug(String slug) { 
            this.slug = slug; 
            return this; 
        }
        
        
        public Builder schemaName(String schemaName) { 
            this.schemaName = schemaName; 
            return this; 
        }
        
        public Builder status(String status) { 
            this.status = status; 
            return this; 
        }
        
        public Builder createdAt(LocalDateTime createdAt) { 
            this.createdAt = createdAt; 
            return this; 
        }
        
        public Builder trialEndDate(LocalDateTime trialEndDate) { 
            this.trialEndDate = trialEndDate; 
            return this; 
        }
        
        public Builder admin(PlatformAdminUserSummaryResponse admin) { 
            this.admin = admin; 
            return this; 
        }
        
        public Builder systemAccount(boolean systemAccount) { 
            this.systemAccount = systemAccount; 
            return this; 
        }
        
        public AccountResponse build() {
            return new AccountResponse(
                id, name, slug, schemaName, status, 
                createdAt, trialEndDate, admin, systemAccount
            );
        }
    }
}