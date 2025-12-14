package brito.com.multitenancy001.entities.tenant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//Sale.java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sales")
public class Sale {
 @Id
 @GeneratedValue(strategy = GenerationType.UUID)
 private String id;
 
 @Column(nullable = false)
 private LocalDateTime saleDate;
 
 @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
 @JoinColumn(name = "sale_id")
 @Builder.Default
 private List<SaleItem> items = new ArrayList<>();
 
 @Column(precision = 10, scale = 2)
 private BigDecimal totalAmount;
 
 private String customerName;
 private String customerEmail;
 
 @Enumerated(EnumType.STRING)
 private SaleStatus status;
 
 public enum SaleStatus {
     PENDING, COMPLETED, CANCELLED, REFUNDED
 }
}
