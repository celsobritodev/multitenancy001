package brito.com.multitenancy001.controlplane.domain.account;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private AccountStatus status = AccountStatus.FREE_TRIAL;

    // ✅ AUDITORIA (técnico)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ✅ NEGÓCIO
    @Column(name = "trial_end_date")
    private LocalDateTime trialEndDate;

    @Column(name = "payment_due_date")
    private LocalDateTime paymentDueDate;

    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate;

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
    private List<ControlPlaneUser> controlPlaneUsers = new ArrayList<>();

    @Column(name = "settings_json", columnDefinition = "TEXT")
    private String settingsJson;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @PrePersist
    protected void onCreate() {
        // base para regras de negócio (não use createdAt aqui)
        LocalDateTime now = LocalDateTime.now();

        // slug
        if (this.slug == null || this.slug.isBlank()) {
            this.slug = this.name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        }

        // schemaName
        if (this.schemaName == null) {
            this.schemaName =
                "tenant_" +
                this.slug.replace("-", "_") +
                "_" +
                UUID.randomUUID().toString().substring(0, 8);
        }

        // trial
        if (this.trialEndDate == null) {
            this.trialEndDate = now.plusDays(30);
        }
    }

    public boolean isTrialActive() {
        return this.status == AccountStatus.FREE_TRIAL &&
               this.trialEndDate != null &&
               this.trialEndDate.isAfter(LocalDateTime.now());
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE ||
               (this.status == AccountStatus.FREE_TRIAL && isTrialActive());
    }

    public boolean isPaymentOverdue() {
        return this.paymentDueDate != null &&
               this.paymentDueDate.isBefore(LocalDateTime.now());
    }

    public long getDaysRemainingInTrial() {
        if (this.trialEndDate == null || !isTrialActive()) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), this.trialEndDate);
    }

    public void softDelete() {
        if (this.deleted) return;
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.status = AccountStatus.CANCELLED;
    }

    public void restore() {
        if (!this.deleted) return;
        this.deleted = false;
        this.deletedAt = null;
        this.status = AccountStatus.ACTIVE;
    }

    public boolean isSystemAccount() {
        return this.systemAccount || "public".equals(this.schemaName);
    }
}
