package brito.com.multitenancy001.platform.domain.tenant;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import brito.com.multitenancy001.platform.domain.user.PlatformUser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
		name = "accounts", uniqueConstraints = {
    @UniqueConstraint(columnNames = "slug"),
    @UniqueConstraint(columnNames = "schema_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "is_system_account", nullable = false)
    @Builder.Default
    private boolean systemAccount = false;
    
    
    @Column(nullable = false, length = 150)
    private String name;
    
    @Column(name = "schema_name", nullable = false, unique = true, length = 100)
    private String schemaName;
    
    @Column(name = "slug", nullable = false, unique = true, length = 50)
    private String slug;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private TenantAccountStatus status = TenantAccountStatus.FREE_TRIAL;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt; // ✅ LocalDate
    
    @Column(name = "trial_end_date")
    private LocalDateTime trialEndDate; // ✅ LocalDate
    
    @Column(name = "payment_due_date")
    private LocalDateTime paymentDueDate; // ✅ LocalDate
    
    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate; // ✅ LocalDate
    
    @Column(name = "subscription_plan", length = 50)
    @Builder.Default
    private String subscriptionPlan = "FREE";
    
    @Column(name = "max_users")
    @Builder.Default
    private Integer maxUsers = 5;
    
    @Column(name = "max_products")
    @Builder.Default
    private Integer maxProducts = 100;
    
    @Column(name = "max_storage_mb")
    @Builder.Default
    private Integer maxStorageMb = 100;
    
    @Column(name = "company_document", nullable = false, length = 20)
    private String companyDocument;
    
    @Column(name = "company_phone", length = 20)
    private String companyPhone;
    
    @Column(name = "company_email", nullable = false, length = 150)
    private String companyEmail;
    
    @Column(name = "company_address", length = 500)
    private String companyAddress;
    
    @Column(name = "company_city", length = 100)
    private String companyCity;
    
    @Column(name = "company_state", length = 50)
    private String companyState;
    
    @Column(name = "company_country", length = 50)
    @Builder.Default
    private String companyCountry = "Brasil";
    
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";
    
    @Column(name = "locale", length = 10)
    @Builder.Default
    private String locale = "pt_BR";
    
    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "BRL";
    
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<PlatformUser> userAccount = new ArrayList<>();
    
    
    
    
    @Column(name = "settings_json", columnDefinition = "TEXT")
    private String settingsJson;
    
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // ✅ LocalDateTime para auditoria
    
    @Column(name = "deleted")
    @Builder.Default
    private boolean deleted = false;
    
    @PrePersist
    protected void onCreate() {

        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }

        // 🔹 Gera slug automaticamente se não vier preenchido
        if (this.slug == null || this.slug.isBlank()) {
            this.slug = this.name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        }

        // 🔹 Gera schemaName automaticamente se não vier preenchido
        if (this.schemaName == null) {
            this.schemaName =
                "tenant_" +
                this.slug.replace("-", "_") +
                "_" +
                UUID.randomUUID().toString().substring(0, 8);
        }

        // 🔹 Define trial automaticamente
        if (this.trialEndDate == null) {
            this.trialEndDate = this.createdAt.plusDays(30);
        }
    }
    
    
    

    @PreUpdate
    protected void onUpdate() {
        // Método para lógica de atualização se necessário
    }
    
    /**
     * Verifica se a conta está em trial ativo
     */
    public boolean isTrialActive() {
        return this.status == TenantAccountStatus.FREE_TRIAL && 
               this.trialEndDate != null && 
               this.trialEndDate.isAfter(LocalDateTime.now());
    }
    
    /**
     * Verifica se a conta está ativa
     */
    public boolean isActive() {
        return this.status == TenantAccountStatus.ACTIVE || 
               (this.status == TenantAccountStatus.FREE_TRIAL && isTrialActive());
    }
    
    /**
     * Verifica se o pagamento está atrasado
     */
    public boolean isPaymentOverdue() {
        return this.paymentDueDate != null && 
               this.paymentDueDate.isBefore(LocalDateTime.now());
    }
    
    /**
     * Dias restantes no trial
     */
    public long getDaysRemainingInTrial() {
        if (this.trialEndDate == null || !isTrialActive()) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), this.trialEndDate);
    }
    
   /**
 * Soft delete da conta
 */
public void softDelete() {
    if (!this.deleted) {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.status = TenantAccountStatus.CANCELLED;
    }
}

    
    /**
     * Restaura a conta (undo soft delete)
     */
    public void restore() {
        if (this.deleted) {
            this.deleted = false;
            this.deletedAt = null;

            // Regra de negócio: ao restaurar, volta para ACTIVE
            this.status = TenantAccountStatus.ACTIVE;
        }
    }
    
    public boolean isSystemAccount() {
        return this.systemAccount || "public".equals(this.schemaName);
    }
    

    
}