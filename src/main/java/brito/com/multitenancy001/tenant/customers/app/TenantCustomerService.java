// ================================================================================
// Classe: TenantCustomerService
// Pacote: brito.com.multitenancy001.tenant.customers.app
// Descrição: Application Service para o agregado Customer.
//            Regras principais:
//            - Leituras usam @TenantReadOnlyTx.
//            - Escritas usam @TenantTx.
//            - Operações sensíveis NÃO usam findById() genérico do JpaRepository.
//            - Soft delete busca apenas registros não deletados.
//            - Restore busca apenas registros deletados.
// ================================================================================

package brito.com.multitenancy001.tenant.customers.app;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.customers.app.command.CreateCustomerCommand;
import brito.com.multitenancy001.tenant.customers.app.command.UpdateCustomerCommand;
import brito.com.multitenancy001.tenant.customers.domain.Customer;
import brito.com.multitenancy001.tenant.customers.persistence.TenantCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCustomerService {

    private final TenantCustomerRepository tenantCustomerRepository;

    // ============================================================================
    // MÉTODOS DE LEITURA (READ-ONLY)
    // ============================================================================

    /**
     * Retorna todos os clientes não deletados.
     */
    @TenantReadOnlyTx
    public List<Customer> findAll() {
        log.debug("Buscando todos os clientes não deletados.");
        List<Customer> customers = tenantCustomerRepository.findAllNotDeleted();
        log.debug("Encontrados {} clientes.", customers.size());
        return customers;
    }

    /**
     * Retorna todos os clientes ativos e não deletados.
     */
    @TenantReadOnlyTx
    public List<Customer> findActive() {
        log.debug("Buscando todos os clientes ativos e não deletados.");
        List<Customer> customers = tenantCustomerRepository.findAllActiveNotDeleted();
        log.debug("Encontrados {} clientes ativos.", customers.size());
        return customers;
    }

    /**
     * Busca um cliente por ID, garantindo que não esteja deletado.
     */
    @TenantReadOnlyTx
    public Customer findById(UUID id) {
        log.debug("Buscando cliente por ID: {}", id);

        validateId(id);

        return tenantCustomerRepository.findByIdNotDeleted(id)
                .orElseThrow(() -> customerNotFound(id));
    }

    /**
     * Busca clientes não deletados cujo nome contenha o termo.
     */
    @TenantReadOnlyTx
    public List<Customer> searchByName(String name) {
        log.debug("Pesquisando clientes por nome contendo: '{}'", name);

        if (!StringUtils.hasText(name)) {
            log.error("Tentativa de busca com nome vazio.");
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Nome para pesquisa é obrigatório", 400);
        }

        List<Customer> customers = tenantCustomerRepository.searchNotDeletedByName(name.trim());
        log.debug("Pesquisa por nome retornou {} resultados.", customers.size());
        return customers;
    }

    /**
     * Busca clientes não deletados por email.
     */
    @TenantReadOnlyTx
    public List<Customer> findByEmail(String email) {
        log.debug("Buscando clientes por email: '{}'", email);

        if (!StringUtils.hasText(email)) {
            log.error("Tentativa de busca com email vazio.");
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório", 400);
        }

        List<Customer> customers = tenantCustomerRepository.findNotDeletedByEmail(email.trim());
        log.debug("Busca por email retornou {} resultados.", customers.size());
        return customers;
    }

    // ============================================================================
    // MÉTODOS DE ESCRITA (COMMANDS)
    // ============================================================================

    /**
     * Cria um novo cliente.
     */
    @TenantTx
    public Customer create(CreateCustomerCommand cmd) {
        log.info("Iniciando criação de novo cliente com dados: {}", maskSensitiveData(cmd));

        Customer customer = mapToEntity(cmd);
        validateForCreate(customer);

        Customer saved = tenantCustomerRepository.save(customer);
        log.info("Cliente criado com sucesso. ID: {}, Nome: {}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Atualiza um cliente existente.
     */
    @TenantTx
    public Customer update(UUID id, UpdateCustomerCommand cmd) {
        log.info("Iniciando atualização do cliente ID: {}", id);

        Customer existing = findById(id);

        log.debug("Aplicando alterações no cliente ID: {}", id);
        applyUpdates(existing, cmd);

        Customer updated = tenantCustomerRepository.save(existing);
        log.info("Cliente ID: {} atualizado com sucesso.", updated.getId());
        return updated;
    }

    /**
     * Alterna o status active de um cliente.
     */
    @TenantTx
    public Customer toggleActive(UUID id) {
        log.info("Alternando status active do cliente ID: {}", id);

        Customer customer = findById(id);
        customer.toggleActive();

        Customer updated = tenantCustomerRepository.save(customer);
        log.info("Status active do cliente ID: {} alterado para: {}", updated.getId(), updated.isActive());
        return updated;
    }

    /**
     * Aplica soft delete em um cliente.
     *
     * <p>Regra:
     * - só encontra registros não deletados;
     * - se não existir no tenant atual, responde 404.
     * </p>
     */
    @TenantTx
    public void softDelete(UUID id) {
        log.info("Aplicando soft delete no cliente ID: {}", id);

        validateId(id);

        Customer customer = tenantCustomerRepository.findByIdNotDeleted(id)
                .orElseThrow(() -> customerNotFound(id));

        customer.softDelete();
        tenantCustomerRepository.save(customer);

        log.info("Soft delete aplicado ao cliente ID: {}", id);
    }

    /**
     * Restaura um cliente previamente deletado.
     *
     * <p>Regra:
     * - só encontra registros deletados;
     * - se não existir deletado no tenant atual, responde 404.
     * </p>
     */
    @TenantTx
    public Customer restore(UUID id) {
        log.info("Restaurando cliente ID: {}", id);

        validateId(id);

        Customer customer = tenantCustomerRepository.findDeletedById(id)
                .orElseThrow(() -> {
                    log.warn("Cliente deletado não encontrado para restauração. ID: {}", id);
                    return new ApiException(ApiErrorCode.CUSTOMER_NOT_FOUND, "Cliente não encontrado", 404);
                });

        customer.restore();
        Customer restored = tenantCustomerRepository.save(customer);

        log.info("Cliente ID: {} restaurado com sucesso.", restored.getId());
        return restored;
    }

    // ============================================================================
    // MÉTODOS PRIVADOS (HELPERS)
    // ============================================================================

    /**
     * Converte um comando de criação em uma nova entidade Customer.
     */
    private Customer mapToEntity(CreateCustomerCommand cmd) {
        log.trace("Mapeando CreateCustomerCommand para entidade.");

        return Customer.builder()
                .name(cmd.name())
                .email(cmd.email())
                .phone(cmd.phone())
                .document(cmd.document())
                .documentType(cmd.documentType())
                .address(cmd.address())
                .city(cmd.city())
                .state(cmd.state())
                .zipCode(cmd.zipCode())
                .country(cmd.country() != null ? cmd.country() : "Brasil")
                .notes(cmd.notes())
                .active(true)
                .deleted(false)
                .build();
    }

    /**
     * Valida as regras de negócio para criação de um cliente.
     */
    private void validateForCreate(Customer customer) {
        log.trace("Validando regras de negócio para criação do cliente.");

        if (!StringUtils.hasText(customer.getName())) {
            log.error("Validação falhou: nome do cliente é obrigatório.");
            throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome do cliente é obrigatório", 400);
        }
        customer.setName(customer.getName().trim());

        if (StringUtils.hasText(customer.getDocument())) {
            String document = customer.getDocument().trim();

            log.debug("Verificando unicidade do documento: {}", maskDocument(document));

            tenantCustomerRepository.findNotDeletedByDocument(document).ifPresent(existing -> {
                log.warn("Tentativa de criar cliente com documento duplicado: {}", maskDocument(document));
                throw new ApiException(ApiErrorCode.DUPLICATE_ENTRY, "Documento já cadastrado para outro cliente", 409);
            });

            customer.setDocument(document);
        }

        if (StringUtils.hasText(customer.getEmail())) {
            customer.setEmail(customer.getEmail().trim());
        }
    }

    /**
     * Aplica alterações de update na entidade existente.
     */
    private void applyUpdates(Customer existing, UpdateCustomerCommand cmd) {
        log.trace("Aplicando atualizações no cliente ID: {}", existing.getId());

        if (cmd.name() != null) {
            existing.setName(cmd.name().trim());
            log.trace("Campo 'name' atualizado para: {}", existing.getName());
        }

        if (cmd.email() != null) {
            existing.setEmail(StringUtils.hasText(cmd.email()) ? cmd.email().trim() : null);
            log.trace("Campo 'email' atualizado.");
        }

        if (cmd.phone() != null) {
            existing.setPhone(StringUtils.hasText(cmd.phone()) ? cmd.phone().trim() : null);
        }

        if (cmd.address() != null) {
            existing.setAddress(StringUtils.hasText(cmd.address()) ? cmd.address().trim() : null);
        }

        if (cmd.city() != null) {
            existing.setCity(StringUtils.hasText(cmd.city()) ? cmd.city().trim() : null);
        }

        if (cmd.state() != null) {
            existing.setState(StringUtils.hasText(cmd.state()) ? cmd.state().trim() : null);
        }

        if (cmd.zipCode() != null) {
            existing.setZipCode(StringUtils.hasText(cmd.zipCode()) ? cmd.zipCode().trim() : null);
        }

        if (cmd.country() != null) {
            existing.setCountry(StringUtils.hasText(cmd.country()) ? cmd.country().trim() : existing.getCountry());
        }

        if (cmd.notes() != null) {
            existing.setNotes(StringUtils.hasText(cmd.notes()) ? cmd.notes().trim() : null);
        }

        if (cmd.documentType() != null) {
            existing.setDocumentType(StringUtils.hasText(cmd.documentType()) ? cmd.documentType().trim() : null);
        }

        if (cmd.document() != null) {
            String newDocument = cmd.document().trim();

            if (StringUtils.hasText(newDocument)) {
                log.debug("Verificando unicidade do documento durante update: {}", maskDocument(newDocument));

                tenantCustomerRepository.findNotDeletedByDocument(newDocument).ifPresent(other -> {
                    if (!other.getId().equals(existing.getId())) {
                        log.warn(
                                "Tentativa de atualizar para documento já existente em outro cliente. Documento: {}, Cliente conflitante ID: {}",
                                maskDocument(newDocument), other.getId()
                        );
                        throw new ApiException(ApiErrorCode.DUPLICATE_ENTRY,
                                "Documento já cadastrado para outro cliente", 409);
                    }
                });

                existing.setDocument(newDocument);
            } else {
                existing.setDocument(null);
            }
        }
    }

    /**
     * Valida o ID informado.
     */
    private void validateId(UUID id) {
        if (id == null) {
            log.error("Tentativa de operação com ID nulo.");
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "ID do cliente é obrigatório", 400);
        }
    }

    /**
     * Cria a exception padrão de cliente não encontrado.
     */
    private ApiException customerNotFound(UUID id) {
        log.warn("Cliente não encontrado para o ID: {}", id);
        return new ApiException(ApiErrorCode.CUSTOMER_NOT_FOUND, "Cliente não encontrado", 404);
    }

    // ============================================================================
    // MÉTODOS PARA MASCARAR DADOS SENSÍVEIS NOS LOGS
    // ============================================================================

    private String maskDocument(String doc) {
        if (doc == null || doc.length() < 5) {
            return "***";
        }
        return doc.substring(0, 2) + "****" + doc.substring(doc.length() - 2);
    }

    private Object maskSensitiveData(CreateCustomerCommand cmd) {
        return String.format(
                "{name='%s', email='%s', document='%s', ...}",
                cmd.name(),
                cmd.email() != null ? "***" : null,
                maskDocument(cmd.document())
        );
    }
}