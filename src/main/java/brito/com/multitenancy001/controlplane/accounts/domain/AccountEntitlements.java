package brito.com.multitenancy001.controlplane.accounts.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "account_entitlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountEntitlements {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers;

    @Column(name = "max_products", nullable = false)
    private Integer maxProducts;

    @Column(name = "max_storage_mb", nullable = false)
    private Integer maxStorageMb;
}

