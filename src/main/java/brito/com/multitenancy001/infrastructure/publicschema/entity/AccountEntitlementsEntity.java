package brito.com.multitenancy001.infrastructure.publicschema.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "account_entitlements",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_entitlements_account_id", columnNames = {"account_id"})
    }
)
public class AccountEntitlementsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers;

    @Column(name = "max_products", nullable = false)
    private Integer maxProducts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountEntitlementsEntity() { }

    public AccountEntitlementsEntity(Long accountId, Integer maxUsers, Integer maxProducts) {
        this.accountId = accountId;
        this.maxUsers = maxUsers;
        this.maxProducts = maxProducts;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public Integer getMaxUsers() { return maxUsers; }
    public Integer getMaxProducts() { return maxProducts; }

    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }
    public void setMaxProducts(Integer maxProducts) { this.maxProducts = maxProducts; }
}
