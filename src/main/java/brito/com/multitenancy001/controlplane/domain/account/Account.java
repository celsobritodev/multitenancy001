package brito.com.multitenancy001.controlplane.domain.account;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.shared.domain.DomainException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;


@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    @Builder.Default
    private AccountType type = AccountType.TENANT;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "schema_name", nullable = false, unique = true, length = 100)
    private String schemaName;

    @Column(name = "slug", nullable = false, unique = true, length = 50)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private AccountStatus status = AccountStatus.FREE_TRIAL;

    // Auditoria (técnico)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Datas de negócio
    @Column(name = "trial_end_date")
    private LocalDateTime trialEndDate;

    @Column(name = "payment_due_date")
    private LocalDateTime paymentDueDate;

    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false, length = 50)
    @Builder.Default
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;

    // Identidade empresa
    @Column(name = "company_email", nullable = false, length = 150)
    private String companyEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_doc_type", nullable = false, length = 10)
    private DocumentType companyDocType;

    @Column(name = "company_doc_number", nullable = false, length = 20)
    private String companyDocNumber;
    
    @Column(name = "company_phone", length = 20)
    private String companyPhone;

    @Column(name = "company_address", length = 500)
    private String companyAddress;

    @Column(name = "company_city", length = 100)
    private String companyCity;

    @Column(name = "company_state", length = 50)
    private String companyState; 
    

    // Localização
    @Column(name = "company_country", length = 50, nullable = false)
    @Builder.Default
    private String companyCountry = "Brasil";
    
    
    @Column(name = "timezone", length = 50, nullable = false)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(name = "locale", length = 10, nullable = false)
    @Builder.Default
    private String locale = "pt_BR";
    

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "BRL";
    
    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY,
            cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @Builder.Default
    @ToString.Exclude
    private List<ControlPlaneUser> controlPlaneUsers = new ArrayList<>();
    
    
    @Column(name = "settings_json", columnDefinition = "TEXT")
    private String settingsJson;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;


    // Soft delete
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @PrePersist
    protected void onCreate() {
        // ✅ aqui NÃO usar tempo
        // slug
        if (this.slug == null || this.slug.isBlank()) {
            this.slug = (this.name == null ? "conta" : this.name)
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("(^-|-$)", "");
        }

        // schemaName
        if (this.schemaName == null || this.schemaName.isBlank()) {
            this.schemaName =
                    "tenant_" +
                    this.slug.replace("-", "_") +
                    "_" +
                    UUID.randomUUID().toString().substring(0, 8);
        }
    }
    
    

    // =========================
    // Semântica / helpers
    // =========================

    public boolean isTenantAccount() {
        return type == AccountType.TENANT;
    }

    public boolean isSystemAccount() {
        return type == AccountType.SYSTEM;
    }

    public boolean isDeleted() {
        return deleted || deletedAt != null;
    }

    public boolean isTrialActive(LocalDateTime now) {
        return this.status == AccountStatus.FREE_TRIAL
                && this.trialEndDate != null
                && now != null
                && this.trialEndDate.isAfter(now);
    }

    public boolean isActive(LocalDateTime now) {
        if (isDeleted()) return false;
        // SYSTEM sempre ativa (a menos que você queira permitir suspensão do system)
        if (isSystemAccount()) return true;

        return this.status == AccountStatus.ACTIVE
                || (this.status == AccountStatus.FREE_TRIAL && isTrialActive(now));
    }

    public boolean isPaymentOverdue(LocalDateTime now) {
        return this.paymentDueDate != null
                && now != null
                && this.paymentDueDate.isBefore(now);
    }

    public long getDaysRemainingInTrial(LocalDateTime now) {
        if (now == null) return 0;
        if (!isTrialActive(now)) return 0;
        return ChronoUnit.DAYS.between(now.toLocalDate(), this.trialEndDate.toLocalDate());
    }

    public void softDelete(LocalDateTime now) {
        if (this.deleted) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        this.deleted = true;
        this.deletedAt = now;
        this.status = AccountStatus.CANCELLED;
    }

    public void restore() {
        if (!this.deleted) return;

        this.deleted = false;
        this.deletedAt = null;

        // para TENANT restaurado: volta ACTIVE (padrão seu)
        // para SYSTEM: continua ACTIVE
        this.status = AccountStatus.ACTIVE;
    }
    
    
 // =========================
 // Regras de domínio: SYSTEM != cliente
 // =========================

 public void setSubscriptionPlan(SubscriptionPlan plan) {
     if (plan == null) {
         throw new DomainException("subscriptionPlan is required");
     }
     if (this.isSystemAccount() && plan != SubscriptionPlan.SYSTEM) {
         throw new DomainException("SYSTEM account must use SYSTEM plan");
     }
     this.subscriptionPlan = plan;
 }

 public void setStatus(AccountStatus status) {
     if (status == null) {
         throw new DomainException("status is required");
     }
     if (this.isSystemAccount() && status != AccountStatus.ACTIVE) {
         throw new DomainException("SYSTEM account must be ACTIVE");
     }
     this.status = status;
 }

 public void setTrialEndDate(LocalDateTime trialEndDate) {
     if (this.isSystemAccount() && trialEndDate != null) {
         throw new DomainException("SYSTEM account must not have trialEndDate");
     }
     this.trialEndDate = trialEndDate;
 }

 public void setPaymentDueDate(LocalDateTime paymentDueDate) {
     if (this.isSystemAccount() && paymentDueDate != null) {
         throw new DomainException("SYSTEM account must not have paymentDueDate");
     }
     this.paymentDueDate = paymentDueDate;
 }

 /**
  * Quando muda para SYSTEM, aplica defaults coerentes:
  * - status ACTIVE
  * - plan SYSTEM
  * - sem trial/billing dates
  */
 public void setType(AccountType type) {
     if (type == null) {
         throw new DomainException("accountType is required");
     }
     this.type = type;

     if (this.isSystemAccount()) {
         // defaults do SYSTEM
         this.subscriptionPlan = SubscriptionPlan.SYSTEM;
         this.status = AccountStatus.ACTIVE;
         this.trialEndDate = null;
         this.paymentDueDate = null;
         this.nextBillingDate = null;
     }
 }

}
