// ===============================
// Category.java
// ===============================
package brito.com.multitenancy001.entities.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "categories",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_categories_name",
        columnNames = "name"
    )
)
@Getter
@Setter
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=100)
    private String name;

    @Column(nullable=false)
    private Boolean active = true;

    @Column(nullable=false)
    private Boolean deleted = false;
}
