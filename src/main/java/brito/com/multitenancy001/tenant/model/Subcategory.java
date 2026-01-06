// ===============================
// Subcategory.java
// ===============================
package brito.com.multitenancy001.tenant.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name="subcategories",
    uniqueConstraints = @UniqueConstraint(
        name="uk_subcategories_name_category",
        columnNames={"category_id","name"}
    )
)
@Getter
@Setter
public class Subcategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional=false)
    @JoinColumn(
        name="category_id",
        nullable=false,
        foreignKey=@ForeignKey(name="fk_subcategories_category")
    )
    private Category category;

    @Column(nullable=false, length=100)
    private String name;

    @Column(nullable=false)
    private Boolean active = true;

    @Column(nullable=false)
    private Boolean deleted = false;
}
