package brito.com.multitenancy001.controlplane.accounts.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.shared.domain.DomainException;
import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accounts")
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account implements Auditable, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    @Builder.Default
    private AccountType type = AccountType.TENANT;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_origin", nullable = false, length = 20)
    @Builder.Default
    private EntityOrigin  origin = EntityOrigin.ADMIN;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_entity_type", nullable = false, length = 20)
    @Builder.Default
    private LegalEntityType legalEntityType = LegalEntityType.COMPANY;

    @Column(name = "schema_name", nullable = false, unique = true, length = 100)
    private String schemaName;

    @Column(name = "slug", nullable = false, unique = true, length = 80)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private AccountStatus status = AccountStatus.FREE_TRIAL;

    // ===== AUDIT (tempo)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== AUDIT (ator)
    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    // ===== BUSINESS DATES
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

    @Column(name = "login_email", nullable = false, length = 150)
    private String loginEmail;

    @Column(name = "billing_email", length = 150)
    private String billingEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_id_type", length = 20)
    private TaxIdType taxIdType;

    @Column(name = "tax_id_number", length = 40)
    private String taxIdNumber;

    @Column(name = "tax_country_code", length = 2, nullable = false)
    @Builder.Default
    private String taxCountryCode = "BR";

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "country", length = 60, nullable = false)
    @Builder.Default
    private String country = "Brasil";

    @Column(name = "timezone", length = 60, nullable = false)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(name = "locale", length = 20, nullable = false)
    @Builder.Default
    private String locale = "pt_BR";

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "BRL";

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @Builder.Default
    @ToString.Exclude
    private List<ControlPlaneUser> controlPlaneUsers = new ArrayList<>();

    @Column(name = "settings_json", columnDefinition = "TEXT")
    private String settingsJson;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    // ===== SOFT DELETE
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Override
    public boolean isDeleted() {
        return deleted || deletedAt != null;
    }

    @PrePersist
    protected void onCreate() {

        if (origin == null) origin = EntityOrigin.ADMIN;

        if (country == null || country.isBlank()) country = "Brasil";
        if (timezone == null || timezone.isBlank()) timezone = "America/Sao_Paulo";
        if (locale == null || locale.isBlank()) locale = "pt_BR";
        if (currency == null || currency.isBlank()) currency = "BRL";
        if (taxCountryCode == null || taxCountryCode.isBlank()) taxCountryCode = "BR";

        if (displayName == null || displayName.isBlank()) {
            throw new DomainException("displayName is required");
        }

        if (slug == null || slug.isBlank()) {
            slug = displayName.toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("(^-|-$)", "");
        }

        if (schemaName == null || schemaName.isBlank()) {
            schemaName = "tenant_" + slug.replace("-", "_") + "_"
                    + UUID.randomUUID().toString().substring(0, 8);
        }

        if (loginEmail != null) loginEmail = loginEmail.trim().toLowerCase();
        if (billingEmail != null && !billingEmail.isBlank()) billingEmail = billingEmail.trim().toLowerCase();
        if (billingEmail != null && billingEmail.isBlank()) billingEmail = null;

        if (taxIdNumber != null) {
            taxIdNumber = taxIdNumber.replaceAll("\\D+", "");
            if (taxIdNumber.isBlank()) taxIdNumber = null;
        }

        if ((taxIdType == null) != (taxIdNumber == null)) {
            throw new DomainException("taxIdType and taxIdNumber must be provided together");
        }

        if (isBuiltInAccount()) {
            applyBuiltInDefaults();
        }
    }

    public boolean isTenantAccount() { return type == AccountType.TENANT; }
    public boolean isPlatformAccount() { return type == AccountType.PLATFORM; }
    public boolean isBuiltInAccount() { return origin == EntityOrigin.BUILT_IN; }

    public boolean isTrialActive(LocalDateTime now) {
        return status == AccountStatus.FREE_TRIAL
                && trialEndDate != null
                && now != null
                && trialEndDate.isAfter(now);
    }

    public boolean isOperational(LocalDateTime now) {
        if (isDeleted()) return false;
        if (isBuiltInAccount()) return true;
        return status == AccountStatus.ACTIVE
                || (status == AccountStatus.FREE_TRIAL && isTrialActive(now));
    }

    public boolean isPaymentOverdue(LocalDateTime now) {
        return paymentDueDate != null && now != null && paymentDueDate.isBefore(now);
    }

    public long getDaysRemainingInTrial(LocalDateTime now) {
        if (now == null) return 0;
        if (!isTrialActive(now)) return 0;
        return ChronoUnit.DAYS.between(now.toLocalDate(), trialEndDate.toLocalDate());
    }

    public void softDelete(LocalDateTime now) {
        if (deleted) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        if (isBuiltInAccount()) {
            throw new DomainException("BUILT_IN account cannot be deleted");
        }

        deleted = true;
        deletedAt = now;
        status = AccountStatus.CANCELLED;
    }

    public void restore() {
        if (!deleted) return;

        deleted = false;
        deletedAt = null;
        status = AccountStatus.ACTIVE;

        if (isBuiltInAccount()) {
            applyBuiltInDefaults();
        }
    }

    public void setSubscriptionPlan(SubscriptionPlan plan) {
        if (plan == null) throw new DomainException("subscriptionPlan is required");
        if (isBuiltInAccount() && plan != SubscriptionPlan.BUILT_IN_PLAN) {
            throw new DomainException("BUILT_IN account must use BUILT_IN_PLAN");
        }
        subscriptionPlan = plan;
    }

    public void setStatus(AccountStatus newStatus) {
        if (newStatus == null) throw new DomainException("status is required");
        if (isBuiltInAccount() && newStatus != AccountStatus.ACTIVE) {
            throw new DomainException("BUILT_IN account must be ACTIVE");
        }
        status = newStatus;
    }

    public void setTrialEndDate(LocalDateTime newTrialEndDate) {
        if (isBuiltInAccount() && newTrialEndDate != null) {
            throw new DomainException("BUILT_IN account must not have trialEndDate");
        }
        trialEndDate = newTrialEndDate;
    }

    public void setPaymentDueDate(LocalDateTime newPaymentDueDate) {
        if (isBuiltInAccount() && newPaymentDueDate != null) {
            throw new DomainException("BUILT_IN account must not have paymentDueDate");
        }
        paymentDueDate = newPaymentDueDate;
    }

    public void setType(AccountType newType) {
        if (newType == null) throw new DomainException("accountType is required");
        type = newType;

        if (origin == EntityOrigin.BUILT_IN && type != AccountType.PLATFORM) {
            throw new DomainException("BUILT_IN account must be PLATFORM");
        }
    }

    public void setOrigin(EntityOrigin  newOrigin) {
        if (newOrigin == null) throw new DomainException("accountOrigin is required");
        origin = newOrigin;

        if (origin == EntityOrigin.BUILT_IN) {
            type = AccountType.PLATFORM;
            applyBuiltInDefaults();
        }
    }

    private void applyBuiltInDefaults() {
        subscriptionPlan = SubscriptionPlan.BUILT_IN_PLAN;
        status = AccountStatus.ACTIVE;
        trialEndDate = null;
        paymentDueDate = null;
        nextBillingDate = null;
        deleted = false;
        deletedAt = null;
    }
}
