// ================================================================================
// Classe: TenantCustomerController
// Pacote: brito.com.multitenancy001.tenant.customers.api
// Descrição: Controller REST para o recurso de Customers (Clientes) no contexto
//            do Tenant. Implementa os padrões arquiteturais do projeto:
//            - Recebe/retorna apenas DTOs.
//            - Validação com @Valid.
//            - Autorização via @PreAuthorize com permissions do Tenant.
//            - Delega toda a lógica para o Application Service.
//            - Logs detalhados em todos os endpoints.
// ================================================================================

package brito.com.multitenancy001.tenant.customers.api;

import brito.com.multitenancy001.tenant.customers.api.dto.CustomerCreateRequest;
import brito.com.multitenancy001.tenant.customers.api.dto.CustomerResponse;
import brito.com.multitenancy001.tenant.customers.api.dto.CustomerUpdateRequest;
import brito.com.multitenancy001.tenant.customers.api.mapper.CustomerApiMapper;
import brito.com.multitenancy001.tenant.customers.app.TenantCustomerService;
import brito.com.multitenancy001.tenant.customers.domain.Customer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tenant/customers")
@RequiredArgsConstructor
public class TenantCustomerController {

    private final TenantCustomerService tenantCustomerService;
    private final CustomerApiMapper customerApiMapper;

    // ============================================================================
    // ENDPOINTS DE LEITURA (GET)
    // ============================================================================

    /**
     * Lista todos os clientes NÃO DELETADOS.
     * Requer permissão TEN_CUSTOMER_READ.
     */
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CUSTOMER_READ.asAuthority())")
    public ResponseEntity<List<CustomerResponse>> listAll() {
        log.info("Recebida requisição para listar todos os clientes não deletados.");
        List<Customer> customers = tenantCustomerService.findAll();
        List<CustomerResponse> response = customerApiMapper.toResponseList(customers);
        log.info("Retornando {} clientes.", response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Lista todos os clientes ATIVOS e NÃO DELETADOS.
     * Requer permissão TEN_CUSTOMER_READ.
     */
    @GetMapping("/active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CUSTOMER_READ.asAuthority())")
    public ResponseEntity<List<CustomerResponse>> listActive() {
        log.info("Recebida requisição para listar clientes ativos.");
        List<Customer> customers = tenantCustomerService.findActive();
        List<CustomerResponse> response = customerApiMapper.toResponseList(customers);
        log.info("Retornando {} clientes ativos.", response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Busca um cliente por ID (apenas se não deletado).
     * Requer permissão TEN_CUSTOMER_READ.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CUSTOMER_READ.asAuthority())")
    public ResponseEntity<CustomerResponse> getById(@PathVariable UUID id) {
        log.info("Recebida requisição para buscar cliente por ID: {}", id);
        Customer customer = tenantCustomerService.findById(id);
        CustomerResponse response = customerApiMapper.toResponse(customer);
        log.info("Cliente encontrado: ID={}, Nome={}", response.id(), response.name());
        return ResponseEntity.ok(response);
    }

    /**
     * Pesquisa clientes por nome (contém, case-insensitive).
     * Requer permissão TEN_CUSTOMER_READ.
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CUSTOMER_READ.asAuthority())")
    public ResponseEntity<List<CustomerResponse>> searchByName(@RequestParam("name") String name) {
        log.info("Recebida requisição para pesquisar clientes por nome contendo: '{}'", name);
        List<Customer> customers = tenantCustomerService.searchByName(name);
        List<CustomerResponse> response = customerApiMapper.toResponseList(customers);
        log.info("Pesquisa retornou {} clientes.", response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Busca clientes por email (exato).
     * Requer permissão TEN_CUSTOMER_READ.
     */
    @GetMapping("/email")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CUSTOMER_READ.asAuthority())")
    public ResponseEntity<List<CustomerResponse>> getByEmail(@RequestParam("email") String email) {
        log.info("Recebida requisição para buscar clientes por email: '{}'", email);
        List<Customer> customers = tenantCustomerService.findByEmail(email);
        List<CustomerResponse> response = customerApiMapper.toResponseList(customers);
        log.info("Busca por email retornou {} clientes.", response.size());
        return ResponseEntity.ok(response);
    }

    // ============================================================================
    // ENDPOINTS DE ESCRITA (POST, PUT, PATCH, DELETE)
    // ============================================================================

    /**
     * Cria um novo cliente.
     * Requer permissão TEN_CUSTOMER_WRITE.
     */
    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CUSTOMER_WRITE.asAuthority())")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerCreateRequest request) {
        log.info("Recebida requisição para criar novo cliente. Dados: name='{}', email='{}', document='***'",
                request.name(), request.email());
        Customer saved = tenantCustomerService.create(customerApiMapper.toCreateCommand(request));
        CustomerResponse response = customerApiMapper.toResponse(saved);
        log.info("Cliente criado com sucesso. ID: {}, Nome: {}", response.id(), response.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Atualiza um cliente existente (comportamento PATCH).
     * Requer permissão TEN_CUSTOMER_WRITE.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CUSTOMER_WRITE.asAuthority())")
    public ResponseEntity<CustomerResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody CustomerUpdateRequest request) {
        log.info("Recebida requisição para atualizar cliente ID: {}", id);
        Customer updated = tenantCustomerService.update(id, customerApiMapper.toUpdateCommand(request));
        CustomerResponse response = customerApiMapper.toResponse(updated);
        log.info("Cliente ID: {} atualizado com sucesso.", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Alterna o status 'active' de um cliente.
     * Requer permissão TEN_CUSTOMER_WRITE.
     */
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CUSTOMER_WRITE.asAuthority())")
    public ResponseEntity<CustomerResponse> toggleActive(@PathVariable UUID id) {
        log.info("Recebida requisição para alternar status active do cliente ID: {}", id);
        Customer updated = tenantCustomerService.toggleActive(id);
        CustomerResponse response = customerApiMapper.toResponse(updated);
        log.info("Status active do cliente ID: {} alterado para: {}", id, updated.isActive());
        return ResponseEntity.ok(response);
    }

    /**
     * Aplica soft delete em um cliente (operação idempotente).
     * Requer permissão TEN_CUSTOMER_DELETE.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CUSTOMER_DELETE.asAuthority())")
    public ResponseEntity<Void> softDelete(@PathVariable UUID id) {
        log.info("Recebida requisição para soft delete do cliente ID: {}", id);
        tenantCustomerService.softDelete(id);
        log.info("Operação de soft delete concluída para o cliente ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restaura um cliente previamente deletado.
     * Requer permissão TEN_CUSTOMER_WRITE.
     */
    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CUSTOMER_WRITE.asAuthority())")
    public ResponseEntity<CustomerResponse> restore(@PathVariable UUID id) {
        log.info("Recebida requisição para restaurar cliente ID: {}", id);
        Customer restored = tenantCustomerService.restore(id);
        CustomerResponse response = customerApiMapper.toResponse(restored);
        log.info("Cliente ID: {} restaurado com sucesso.", id);
        return ResponseEntity.ok(response);
    }
}