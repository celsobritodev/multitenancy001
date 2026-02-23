// src/main/java/brito/com/multitenancy001/controlplane/accounts/domain/Account.java
// Versão completa e corrigida

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

/**
 * Agregado raiz que representa uma conta (tenant) no sistema.
 * 
 * <p>Uma conta pode ser de dois tipos:</p>
 * <ul>
 *   <li>{@link AccountType#TENANT} - conta de cliente (tenant)</li>
 *   <li>{@link AccountType#PLATFORM} - conta interna da plataforma (Control Plane)</li>
 * </ul>
 * 
 * <p>As contas de cliente (TENANT) possuem um schema de banco de dados dedicado
 * ({@link #tenantSchema}) onde residem todos os dados específicos do tenant,
 * como usuários, produtos, categorias, etc.</p>
 * 
 * <p>Regras de negócio importantes:</p>
 * <ul>
 *   <li>O status da conta ({@link AccountStatus}) determina se ela está operacional</li>
 *   <li>Contas podem ser suspensas por inadimplência ou ação administrativa</li>
 *   <li>Soft delete ({@link #deleted}) é usado para remoção lógica</li>
 *   <li>Contas BUILT_IN/PLATFORM são protegidas contra exclusão</li>
 * </ul>
 * 
 * @see AccountStatus
 * @see AccountType
 * @see brito.com.multitenancy001.controlplane.accounts.app.AccountAppService
 */
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
     * Schema do banco de dados onde residem os dados do tenant.
     */
    @Column(name = "tenant_schema", nullable = false, unique = true, length = 100)
    private String tenantSchema;

    @Column(name = "slug", nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "tax_country_code", nullable = false, length = 2)
    @Builder.Default
    private String taxCountryCode = "BR";

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_id_type", length = 20)
    private TaxIdType taxIdType;

    @Column(name = "tax_id_number", length = 40)
    private String taxIdNumber;

    @Column(name = "login_email", nullable = false, columnDefinition = "citext")
    private String loginEmail;

    @Column(name = "billing_email", columnDefinition = "citext")
    private String billingEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private AccountStatus status = AccountStatus.PROVISIONING;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false, length = 50)
    @Builder.Default
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;

    @Column(name = "trial_end_at", columnDefinition = "timestamptz")
    private Instant trialEndAt;

    @Column(name = "payment_due_date", columnDefinition = "date")
    private LocalDate paymentDueDate;

    @Column(name = "next_billing_date", columnDefinition = "date")
    private LocalDate nextBillingDate;

    // =========================
    // Informações de contato
    // =========================

    @Column(length = 20)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 60)
    @Builder.Default
    private String country = "Brasil";

    @Column(length = 60)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(length = 20)
    @Builder.Default
    private String locale = "pt_BR";

    @Column(length = 3)
    @Builder.Default
    private String currency = "BRL";

    // =========================
    // Configurações adicionais
    // =========================

    @Column(name = "settings_json", columnDefinition = "TEXT")
    private String settingsJson;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    // =========================
    // Soft delete
    // =========================

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    // =========================
    // Relacionamentos
    // =========================

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

    /**
     * Verifica se a conta é uma conta interna do sistema (BUILT_IN/PLATFORM).
     * 
     * @return true se for conta do sistema
     */
    public boolean isBuiltInAccount() {
        return origin == EntityOrigin.BUILT_IN || type == AccountType.PLATFORM;
    }

    // =========================
    // Soft delete / Restore
    // =========================

    /**
     * Aplica soft delete na conta.
     * 
     * @param now instante atual (deve vir do AppClock)
     * @throws DomainException se now for null
     */
    public void softDelete(Instant now) {
        if (now == null) throw new DomainException("now é obrigatório");
        if (this.deleted) return;

        this.deleted = true;
        if (this.audit != null) {
            this.audit.markDeleted(now);
        }
    }

    /**
     * Restaura uma conta previamente deletada.
     */
    public void restore() {
        if (!this.deleted) return;

        this.deleted = false;
        if (this.audit != null) {
            this.audit.clearDeleted();
        }
    }

    public void setDeletedAt(Instant deletedAt) {
        if (this.audit != null) {
            this.audit.setDeletedAt(deletedAt);
        }
        this.deleted = deletedAt != null;
    }

    // =========================
    // Regras de domínio
    // =========================

    /**
     * Verifica se a conta está operacional (não deletada e status operacional).
     * 
     * @return true se a conta puder ser usada
     */
    public boolean isOperational() {
        return !deleted && status != null && status.isOperational();
    }

    /**
     * Lança exceção se a conta não estiver operacional.
     * 
     * @throws DomainException se a conta não estiver operacional
     */
    public void requireOperational() {
        if (!isOperational()) throw new DomainException("Conta não está operacional");
    }

    /**
     * Define o nome de exibição com validação.
     * 
     * @param value nome de exibição
     * @throws DomainException se value for nulo ou vazio
     */
    public void setDisplayNameSafe(String value) {
        if (value == null || value.isBlank()) throw new DomainException("displayName é obrigatório");
        this.displayName = value.trim();
    }

    /**
     * Define o slug com validação e normalização.
     * 
     * @param value slug desejado
     * @throws DomainException se value for inválido
     */
    public void setSlugSafe(String value) {
        if (value == null || value.isBlank()) throw new DomainException("slug é obrigatório");
        this.slug = normalizeSlug(value);
    }

    /**
     * Garante que a conta tenha um tenantSchema.
     * Se não tiver, gera um a partir do slug.
     */
    public void ensureTenantSchema() {
        if (this.tenantSchema == null || this.tenantSchema.isBlank()) {
            this.tenantSchema = generateTenantSchemaFromSlug(this.slug);
        }
    }

    /**
     * Inicia o período de trial gratuito.
     * 
     * @param now instante atual
     * @param days duração do trial em dias
     * @throws DomainException se parâmetros forem inválidos
     */
    public void startFreeTrial(Instant now, int days) {
        if (now == null) throw new DomainException("now é obrigatório");
        if (days <= 0) throw new DomainException("days inválido");

        this.status = AccountStatus.FREE_TRIAL;
        this.subscriptionPlan = SubscriptionPlan.FREE;
        this.trialEndAt = now.plus(days, ChronoUnit.DAYS);
    }

    /**
     * Ativa um plano pago para a conta.
     * 
     * @param plan plano contratado
     * @param paymentDueDate data de vencimento do pagamento
     * @param nextBillingDate próxima data de cobrança
     * @throws DomainException se parâmetros forem inválidos
     */
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

    /**
     * Atualiza as informações de contato da conta.
     */
    public void updateContactInfo(String phone, String address, String city, String state, String country) {
        if (phone != null) this.phone = phone.trim();
        if (address != null) this.address = address.trim();
        if (city != null) this.city = city.trim();
        if (state != null) this.state = state.trim();
        if (country != null && !country.isBlank()) this.country = country.trim();
    }

    /**
     * Atualiza as preferências de localização.
     */
    public void updateLocalization(String timezone, String locale, String currency) {
        if (timezone != null && !timezone.isBlank()) this.timezone = timezone.trim();
        if (locale != null && !locale.isBlank()) this.locale = locale.trim();
        if (currency != null && !currency.isBlank()) this.currency = currency.trim();
    }

    // =========================
    // Helpers
    // =========================

    /**
     * Normaliza um slug para o formato canônico.
     * 
     * @param raw slug bruto
     * @return slug normalizado
     * @throws DomainException se o slug for muito curto
     */
    private static String normalizeSlug(String raw) {
        String v = raw.trim().toLowerCase();
        v = v.replaceAll("[^a-z0-9\\-]", "-");
        v = v.replaceAll("-{2,}", "-");
        v = v.replaceAll("(^-|-$)", "");
        if (v.length() < 3) throw new DomainException("slug muito curto");
        return v;
    }

    /**
     * Gera um nome de schema único a partir do slug.
     * 
     * @param slug slug base
     * @return nome do schema no formato "t_<slug>_<random>"
     */
    private static String generateTenantSchemaFromSlug(String slug) {
        String base = (slug == null ? "tenant" : slug.replace("-", "_"));
        base = base.replaceAll("[^a-z0-9_]", "");
        if (base.length() > 40) base = base.substring(0, 40);
        return "t_" + base + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}