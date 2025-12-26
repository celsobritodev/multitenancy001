package brito.com.multitenancy001.entities.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.ForeignKey;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="subcategories",
       uniqueConstraints = @UniqueConstraint(
         name="uk_subcategories_name_category",
         columnNames={"category_id","name"}
       ))
@Getter @Setter
public class Subcategory {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional=false)
  @JoinColumn(name="category_id", foreignKey=@ForeignKey(name="fk_subcategories_category"))
  private Category category;

  @Column(nullable=false, length=100)
  private String name;

  @Column(nullable=false)
  private Boolean active = true;

  @Column(nullable=false)
  private Boolean deleted = false;
}
