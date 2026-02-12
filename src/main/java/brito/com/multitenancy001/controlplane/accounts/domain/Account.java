package brito.com.multitenancy001.controlplane.accounts.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private EntityOrigin origin = EntityOrigin.ADMIN;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_entity_type", nullable = false, length = 20)
    @Builder.Default
    private LegalEntityType legalEntityType = LegalEntityType.COMPANY;

    /**
     * ✅ Semântica no código: tenantSchema
     * ✅ Banco permanece: schema_name
     */
    @Column(name = "schema_name", nullable = false, unique = true, length = 100)
    private String tenantSchema;

    @Column(name = "slug", nullable = false, unique = true, length = 80)
    private String slug;

    // ✅ Alinhado com migration: VARCHAR(2) NOT NULL DEFAULT 'BR'
    @Column(name = "tax_country_code", nullable = false, length = 2)
    @Builder.Default
    private String taxCountryCode = "BR";

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_id_type", length = 20)
    private TaxIdType taxIdType;

    // ✅ Alinhado com migration: VARCHAR(40)
    @Column(name = "tax_id_number", length = 40)
    private String taxIdNumber;

    // ✅ Alinhado com migration: CITEXT NOT NULL
    @Column(name = "login_email", nullable = false, columnDefinition = "citext")
    private String loginEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private AccountStatus status = AccountStatus.PROVISIONING;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false, length = 50)
    @Builder.Default
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;

    /**
     * Instante real (ex.: fim do trial como momento absoluto).
     * Instant <-> TIMESTAMPTZ
     */
    @Column(name = "trial_end_date", columnDefinition = "timestamptz")
    private Instant trialEndAt;

    /**
     * Data civil (vencimento no dia X, sem horário).
     * LocalDate <-> DATE
     */
    @Column(name = "payment_due_date", columnDefinition = "date")
    private LocalDate paymentDueDate;

    /**
     * Data civil (próxima cobrança no dia X).
     * LocalDate <-> DATE
     */
    @Column(name = "next_billing_date", columnDefinition = "date")
    private LocalDate nextBillingDate;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ControlPlaneUser> controlPlaneUsers = new ArrayList<>();

    // =========================
    // Auditable / SoftDeletable
    // =========================

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    // =========================
    // Semântica de sistema
    // =========================

    public boolean isBuiltInAccount() {
        return origin == EntityOrigin.BUILT_IN || type == AccountType.PLATFORM;
    }

    // =========================
    // Soft delete / Restore
    // =========================

    public void softDelete(Instant now) {
        if (now == null) throw new DomainException("now é obrigatório");
        if (this.deleted) return;

        this.deleted = true;
        if (this.audit != null) {
            tryMarkDeleted(now);
        }
    }

    public void restore() {
        if (!this.deleted) return;

        this.deleted = false;
        if (this.audit != null) {
            tryClearDeleted();
        }
    }

    public void setDeletedAt(Instant deletedAt) {
        if (this.audit != null) {
            trySetDeletedAt(deletedAt);
        }
        this.deleted = deletedAt != null;
    }

    private void tryMarkDeleted(Instant now) {
        try {
            audit.markDeleted(now);
        } catch (Throwable ignore) {
            trySetDeletedAt(now);
        }
    }

    private void tryClearDeleted() {
        try {
            audit.clearDeleted();
        } catch (Throwable ignore) {
            trySetDeletedAt(null);
        }
    }

    private void trySetDeletedAt(Instant v) {
        try {
            audit.setDeletedAt(v);
        } catch (Throwable ignore) {
            // best-effort
        }
    }

    // =========================
    // Regras de domínio
    // =========================

    public boolean isOperational() {
        return !deleted && status != null && status.isOperational();
    }

    public void requireOperational() {
        if (!isOperational()) {
            throw new DomainException("Conta não está operacional");
        }
    }

    public void setDisplayNameSafe(String value) {
        if (value == null || value.isBlank()) throw new DomainException("displayName é obrigatório");
        this.displayName = value.trim();
    }

    public void setSlugSafe(String value) {
        if (value == null || value.isBlank()) throw new DomainException("slug é obrigatório");
        this.slug = normalizeSlug(value);
    }

    /**
     * ✅ Antes: ensureSchemaName()
     * ✅ Agora: ensureTenantSchema()
     */
    public void ensureTenantSchema() {
        if (this.tenantSchema == null || this.tenantSchema.isBlank()) {
            this.tenantSchema = generateTenantSchemaFromSlug(this.slug);
        }
    }

    public void startFreeTrial(Instant now, int days) {
        if (now == null) throw new DomainException("now é obrigatório");
        if (days <= 0) throw new DomainException("days inválido");

        this.status = AccountStatus.FREE_TRIAL;
        this.subscriptionPlan = SubscriptionPlan.FREE;
        this.trialEndAt = now.plus(days, ChronoUnit.DAYS);
    }

    public void activatePaidPlan(SubscriptionPlan plan, LocalDate paymentDueDate, LocalDate nextBillingDate) {
        if (plan == null) throw new DomainException("plan é obrigatório");
        if (plan == SubscriptionPlan.FREE) throw new DomainException("plan inválido");
        if (paymentDueDate == null) throw new DomainException("paymentDueDate é obrigatório");
        if (nextBillingDate == null) throw new DomainException("nextBillingDate é obrigatório");

        this.status = AccountStatus.ACTIVE;
        this.subscriptionPlan = plan;
        this.paymentDueDate = paymentDueDate;
        this.nextBillingDate = nextBillingDate;
    }

    // =========================
    // Helpers
    // =========================

    private static String normalizeSlug(String raw) {
        String v = raw.trim().toLowerCase();
        v = v.replaceAll("[^a-z0-9\\-]", "-");
        v = v.replaceAll("-{2,}", "-");
        v = v.replaceAll("(^-|-$)", "");
        if (v.length() < 3) throw new DomainException("slug muito curto");
        return v;
    }

    private static String generateTenantSchemaFromSlug(String slug) {
        String base = (slug == null ? "tenant" : slug.replace("-", "_"));
        base = base.replaceAll("[^a-z0-9_]", "");
        if (base.length() > 40) base = base.substring(0, 40);
        return "t_" + base + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
