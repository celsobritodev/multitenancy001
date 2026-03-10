// ================================================================================
// Classe: TenantCustomerService (CORRIGIDA)
// Pacote: brito.com.multitenancy001.tenant.customers.app
// Descrição: Application Service para o agregado Customer.
//            Versão corrigida para usar os ApiErrorCode existentes no projeto.
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
    // CONSTANTES PARA CÓDIGOS DE ERRO (usando ApiErrorCode existente)
    // ============================================================================
    
    // ⚠️ IMPORTANTE: Seu projeto NÃO TEM ApiErrorCode.ENTITY_NOT_FOUND
    // Vamos usar os códigos específicos que seguem o padrão do projeto.
    // Você PRECISARÁ adicionar estes códigos no seu ApiErrorCode.java
    // seguindo o padrão existente (ex: PRODUCT_NOT_FOUND, SUPPLIER_NOT_FOUND)
    
    // ============================================================================
    // MÉTODOS DE LEITURA (READ-ONLY)
    // ============================================================================

    /**
     * Retorna todos os clientes NÃO DELETADOS.
     */
    @TenantReadOnlyTx
    public List<Customer> findAll() {
        log.debug("Buscando todos os clientes não deletados.");
        List<Customer> customers = tenantCustomerRepository.findAllNotDeleted();
        log.debug("Encontrados {} clientes.", customers.size());
        return customers;
    }

    /**
     * Retorna todos os clientes ATIVOS e NÃO DELETADOS.
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
     * Lança ApiException (404) se não encontrado.
     */
    @TenantReadOnlyTx
    public Customer findById(UUID id) {
        log.debug("Buscando cliente por ID: {}", id);
        if (id == null) {
            log.error("Tentativa de busca com ID nulo.");
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "ID do cliente é obrigatório", 400);
        }

        return tenantCustomerRepository.findByIdNotDeleted(id)
                .orElseThrow(() -> {
                    log.warn("Cliente não encontrado para o ID: {}", id);
                    // ⚠️ Você PRECISARÁ adicionar CUSTOMER_NOT_FOUND no ApiErrorCode
                    throw new ApiException(ApiErrorCode.CUSTOMER_NOT_FOUND, "Cliente não encontrado", 404);
                });
    }

    /**
     * Busca clientes NÃO DELETADOS cujo nome contenha o termo.
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
     * Busca clientes NÃO DELETADOS por email.
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
     * Valida unicidade de documento antes de persistir.
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
     * Atualiza um cliente existente (comportamento PATCH).
     * - Campos null no command não são alterados.
     * - Valida unicidade de documento se ele for alterado.
     */
    @TenantTx
    public Customer update(UUID id, UpdateCustomerCommand cmd) {
        log.info("Iniciando atualização do cliente ID: {}", id);
        Customer existing = findById(id); // Reutiliza validação de existência

        log.debug("Aplicando alterações no cliente ID: {}", id);
        applyUpdates(existing, cmd);

        Customer updated = tenantCustomerRepository.save(existing);
        log.info("Cliente ID: {} atualizado com sucesso.", updated.getId());
        return updated;
    }

    /**
     * Alterna o status 'active' de um cliente.
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
     * Aplica soft delete em um cliente (operação idempotente).
     */
    @TenantTx
    public void softDelete(UUID id) {
        log.info("Aplicando soft delete no cliente ID: {}", id);
        if (id == null) {
            log.error("Tentativa de soft delete com ID nulo.");
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "ID do cliente é obrigatório", 400);
        }

        tenantCustomerRepository.findById(id).ifPresentOrElse(
                customer -> {
                    if (!customer.isDeleted()) {
                        customer.softDelete();
                        tenantCustomerRepository.save(customer);
                        log.info("Soft delete aplicado ao cliente ID: {}", id);
                    } else {
                        log.debug("Cliente ID: {} já estava deletado. Nenhuma ação necessária.", id);
                    }
                },
                () -> log.debug("Cliente ID: {} não encontrado para soft delete (idempotência).", id)
        );
    }

    /**
     * Restaura um cliente previamente deletado.
     */
    @TenantTx
    public Customer restore(UUID id) {
        log.info("Restaurando cliente ID: {}", id);
        Customer customer = tenantCustomerRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Cliente não encontrado para restauração. ID: {}", id);
                    // ⚠️ Você PRECISARÁ adicionar CUSTOMER_NOT_FOUND no ApiErrorCode
                    throw new ApiException(ApiErrorCode.CUSTOMER_NOT_FOUND, "Cliente não encontrado", 404);
                });

        if (!customer.isDeleted()) {
            log.debug("Cliente ID: {} já não está deletado. Retornando entidade atual.", id);
            return customer;
        }

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
                .active(true)       // Cliente novo começa ativo
                .deleted(false)     // Cliente novo não está deletado
                .build();
    }

    /**
     * Valida as regras de negócio para criação de um cliente.
     * - Nome é obrigatório.
     * - Documento, se presente, deve ser único.
     */
    private void validateForCreate(Customer customer) {
        log.trace("Validando regras de negócio para criação do cliente.");

        if (!StringUtils.hasText(customer.getName())) {
            log.error("Validação falhou: nome do cliente é obrigatório.");
            throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome do cliente é obrigatório", 400);
        }
        customer.setName(customer.getName().trim());

        if (StringUtils.hasText(customer.getDocument())) {
            String doc = customer.getDocument().trim();
            log.debug("Verificando unicidade do documento: {}", maskDocument(doc));
            tenantCustomerRepository.findNotDeletedByDocument(doc)
                    .ifPresent(existing -> {
                        log.warn("Tentativa de criar cliente com documento duplicado: {}", maskDocument(doc));
                        // ⚠️ Usando DUPLICATE_ENTRY que já existe no seu enum
                        throw new ApiException(ApiErrorCode.DUPLICATE_ENTRY, "Documento já cadastrado para outro cliente", 409);
                    });
            customer.setDocument(doc);
        }

        // Normalizações adicionais podem ser adicionadas aqui (email, phone, etc.)
        if (StringUtils.hasText(customer.getEmail())) {
            customer.setEmail(customer.getEmail().trim());
        }
    }

    /**
     * Aplica as alterações de um comando de update na entidade existente.
     * Apenas campos não-nulos são alterados (PATCH).
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

        // Lógica especial para documento (precisa verificar unicidade se for alterado)
        if (cmd.document() != null) {
            String newDoc = cmd.document().trim();
            if (StringUtils.hasText(newDoc)) {
                log.debug("Verificando unicidade do documento durante update: {}", maskDocument(newDoc));
                tenantCustomerRepository.findNotDeletedByDocument(newDoc)
                        .ifPresent(other -> {
                            if (!other.getId().equals(existing.getId())) {
                                log.warn("Tentativa de atualizar para documento já existente em outro cliente. Documento: {}, Cliente conflitante ID: {}",
                                        maskDocument(newDoc), other.getId());
                                // ⚠️ Usando DUPLICATE_ENTRY que já existe no seu enum
                                throw new ApiException(ApiErrorCode.DUPLICATE_ENTRY, "Documento já cadastrado para outro cliente", 409);
                            }
                        });
                existing.setDocument(newDoc);
            } else {
                existing.setDocument(null); // Permite limpar o documento
            }
        }
    }

    // ============================================================================
    // MÉTODOS PARA MASCARAR DADOS SENSÍVEIS NOS LOGS
    // ============================================================================

    private String maskDocument(String doc) {
        if (doc == null || doc.length() < 5) return "***";
        return doc.substring(0, 2) + "****" + doc.substring(doc.length() - 2);
    }

    private Object maskSensitiveData(CreateCustomerCommand cmd) {
        // Retorna uma representação segura para log (evita expor documento/email completo)
        return String.format(
                "{name='%s', email='%s', document='%s', ...}",
                cmd.name(),
                cmd.email() != null ? "***" : null,
                maskDocument(cmd.document())
        );
    }
}