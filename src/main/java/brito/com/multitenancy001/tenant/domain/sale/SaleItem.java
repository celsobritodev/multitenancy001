package brito.com.multitenancy001.tenant.domain.sale;



import jakarta.persistence.*;

@Entity  // ← ESTA ANOTAÇÃO É OBRIGATÓRIA!
@Table(name = "sale_items")
public class SaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ... outros campos
    

    @ManyToOne
    @JoinColumn(name = "sale_id")
    private Sale sale;
    
    // Getters e setters
}