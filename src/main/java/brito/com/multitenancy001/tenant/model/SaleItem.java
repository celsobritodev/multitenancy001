package brito.com.multitenancy001.tenant.model;



import jakarta.persistence.*;

@Entity  // ← ESTA ANOTAÇÃO É OBRIGATÓRIA!
@Table(name = "sale_items")
public class SaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ... outros campos
    
    // VERIFIQUE SE TEM @ManyToOne para Sale
    @ManyToOne
    @JoinColumn(name = "sale_id")
    private Sale sale;
    
    // Getters e setters
}