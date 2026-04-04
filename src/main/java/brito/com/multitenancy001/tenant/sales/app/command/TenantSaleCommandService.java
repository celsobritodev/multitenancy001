package brito.com.multitenancy001.tenant.sales.app.command;

import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.tenant.sales.api.dto.SaleCreateRequest;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina de command do módulo Sales.
 *
 * <p>Responsável apenas por preservar compatibilidade com os chamadores
 * e delegar os casos de uso de mutação para serviços especializados.</p>
 *
 * <p>Casos de uso delegados:</p>
 * <ul>
 *   <li>Criação de venda</li>
 *   <li>Atualização de venda</li>
 *   <li>Delete lógico</li>
 *   <li>Restore</li>
 * </ul>
 *
 * <p>Regras arquiteturais preservadas:</p>
 * <ul>
 *   <li>Controller continua fino</li>
 *   <li>Command separado por caso de uso</li>
 *   <li>Boundary tenant permanece explícito</li>
 *   <li>Compatibilidade da API interna preservada</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSaleCommandService {

    private final TenantSaleCreateCommandService createCommandService;
    private final TenantSaleUpdateCommandService updateCommandService;
    private final TenantSaleDeleteCommandService deleteCommandService;
    private final TenantSaleRestoreCommandService restoreCommandService;

    /**
     * Cria uma nova venda.
     *
     * @param accountId account atual
     * @param tenantSchema schema do tenant atual
     * @param req payload de criação
     * @return venda criada
     */
    public SaleResponse create(Long accountId, String tenantSchema, SaleCreateRequest req) {
        log.debug(
                "SALE_COMMAND_FACADE_CREATE | accountId={} | tenantSchema={}",
                accountId,
                tenantSchema
        );
        return createCommandService.create(accountId, tenantSchema, req);
    }

    /**
     * Atualiza uma venda existente.
     *
     * @param accountId account atual
     * @param tenantSchema schema do tenant atual
     * @param saleId id da venda
     * @param req payload de atualização
     * @return venda atualizada
     */
    public SaleResponse update(Long accountId, String tenantSchema, UUID saleId, SaleUpdateRequest req) {
        log.debug(
                "SALE_COMMAND_FACADE_UPDATE | accountId={} | tenantSchema={} | saleId={}",
                accountId,
                tenantSchema,
                saleId
        );
        return updateCommandService.update(accountId, tenantSchema, saleId, req);
    }

    /**
     * Deleta logicamente uma venda.
     *
     * @param accountId account atual
     * @param tenantSchema schema do tenant atual
     * @param saleId id da venda
     */
    public void delete(Long accountId, String tenantSchema, UUID saleId) {
        log.debug(
                "SALE_COMMAND_FACADE_DELETE | accountId={} | tenantSchema={} | saleId={}",
                accountId,
                tenantSchema,
                saleId
        );
        deleteCommandService.delete(accountId, tenantSchema, saleId);
    }

    /**
     * Restaura uma venda deletada logicamente.
     *
     * @param accountId account atual
     * @param tenantSchema schema do tenant atual
     * @param saleId id da venda
     * @return venda restaurada
     */
    public SaleResponse restore(Long accountId, String tenantSchema, UUID saleId) {
        log.debug(
                "SALE_COMMAND_FACADE_RESTORE | accountId={} | tenantSchema={} | saleId={}",
                accountId,
                tenantSchema,
                saleId
        );
        return restoreCommandService.restore(accountId, tenantSchema, saleId);
    }
}