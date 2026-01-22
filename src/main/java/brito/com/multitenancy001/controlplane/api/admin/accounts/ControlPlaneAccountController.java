package brito.com.multitenancy001.controlplane.api.admin.accounts;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.summary.AccountTenantUserSummaryResponse;
import brito.com.multitenancy001.controlplane.application.AccountLifecycleService;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.shared.api.error.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountController {

    private final AccountLifecycleService accountLifecycleService;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private Pageable pageableOrDefault(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }

        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();

        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;

        return PageRequest.of(page, size, pageable.getSort());
    }

    // Lista contas (não deletadas) com paginação, ordenadas por criação (mais recentes primeiro).
    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Page<AccountResponse>> listAccountsLatest(Pageable pageable) {
        Pageable p = pageableOrDefault(pageable);
        return ResponseEntity.ok(accountLifecycleService.listAccountsLatest(p));
    }

    // Lista todas as contas (não deletadas) sem paginação.
    @GetMapping
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        log.info("Listando todas as contas");
        return ResponseEntity.ok(accountLifecycleService.listAccounts());
    }

    // Busca uma conta (não deletada) por ID.
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountLifecycleService.getAccount(id));
    }

    // Busca detalhes administrativos de uma conta (inclui contagem de usuários da plataforma vinculados).
    @GetMapping("/{id}/details")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountAdminDetailsResponse> getAccountDetails(@PathVariable Long id) {
        return ResponseEntity.ok(accountLifecycleService.getAccountAdminDetails(id));
    }

    // Lista usuários TENANT vinculados à conta (opcionalmente apenas operacionais).
    @GetMapping("/{id}/users")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountTenantUserSummaryResponse>> listUsersByAccount(@PathVariable Long id) {
        log.info("Listando Usuários por conta");
        return ResponseEntity.ok(accountLifecycleService.listTenantUsers(id, false));
    }

    // Lista usuários TENANT operacionais vinculados à conta (ex.: não suspensos/operacionais conforme regra do serviço).
    @GetMapping("/{id}/users/operational")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountTenantUserSummaryResponse>> listOperationalUsersByAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountLifecycleService.listTenantUsers(id, true));
    }

    // Busca uma conta (não deletada) por slug (case-insensitive).
    @GetMapping("/by-slug/{slug}")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountResponse> getBySlugIgnoreCase(@PathVariable String slug) {
        return ResponseEntity.ok(accountLifecycleService.getAccountBySlugIgnoreCase(slug));
    }

    // Lista contas por status (não deletadas) com paginação.
    @GetMapping("/by-status/{status}")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Page<AccountResponse>> listByStatus(@PathVariable AccountStatus status, Pageable pageable) {
        Pageable p = pageableOrDefault(pageable);
        return ResponseEntity.ok(accountLifecycleService.listAccountsByStatus(status, p));
    }

    // Lista contas por múltiplos status (não deletadas) sem paginação.
    @GetMapping("/by-statuses")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountResponse>> listByStatuses(@RequestParam("statuses") List<AccountStatus> statuses) {
        return ResponseEntity.ok(accountLifecycleService.listAccountsByStatuses(statuses));
    }

    // Busca contas por termo em displayName/legalName (não deletadas) com paginação explícita.
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Page<AccountResponse>> searchByDisplayName(
            @RequestParam("term") String term,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    ) {
        if (page < 0) page = 0;

        if (size <= 0) {
            throw new ApiException("INVALID_PAGINATION", "size deve ser > 0", 400);
        }
        if (size > MAX_PAGE_SIZE) {
            throw new ApiException("INVALID_PAGINATION", "size máximo é " + MAX_PAGE_SIZE, 400);
        }

        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(accountLifecycleService.searchAccountsByDisplayName(term, p));
    }

    // Lista contas criadas entre duas datas (não deletadas) com paginação.
    @GetMapping("/created-between")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Page<AccountResponse>> listCreatedBetween(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Pageable pageable
    ) {
        Pageable p = pageableOrDefault(pageable);
        return ResponseEntity.ok(accountLifecycleService.listAccountsCreatedBetween(start, end, p));
    }

    // Conta quantas contas (não deletadas) existem em um status.
    @GetMapping("/count/by-status/{status}")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Long> countByStatus(@PathVariable AccountStatus status) {
        return ResponseEntity.ok(accountLifecycleService.countAccountsByStatus(status));
    }

    // Conta quantas contas operacionais existem (definição centralizada no service/repository).
    @GetMapping("/count/operational")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Long> countOperationalAccounts() {
        return ResponseEntity.ok(accountLifecycleService.countOperationalAccounts());
    }

    // Lista contas com trial vencido (opcionalmente informando data/status; defaults no service).
    @GetMapping("/expired-trials")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountResponse>> listExpiredTrials(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date,
            @RequestParam(value = "status", required = false) AccountStatus status
    ) {
        return ResponseEntity.ok(accountLifecycleService.listExpiredTrials(date, status));
    }

    // Lista contas com pagamento vencido (opcionalmente informando data/status; defaults no service).
    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountResponse>> listOverdue(
            @RequestParam(value = "today", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime today,
            @RequestParam(value = "status", required = false) AccountStatus status
    ) {
        return ResponseEntity.ok(accountLifecycleService.listOverdueAccounts(today, status));
    }

    // Altera o status de uma conta e executa efeitos colaterais (ex.: suspender/reativar usuários TENANT).
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('CP_TENANT_SUSPEND','CP_TENANT_RESUME')")
    public ResponseEntity<AccountStatusChangeResponse> changeAccountStatus(
            @PathVariable Long id,
            @Valid @RequestBody AccountStatusChangeRequest accountStatusChangeRequest
    ) {
        return ResponseEntity.ok(accountLifecycleService.changeAccountStatus(id, accountStatusChangeRequest));
    }

    // Executa soft delete em uma conta (e efeitos colaterais associados).
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CP_TENANT_DELETE')")
    public ResponseEntity<Void> softDeleteAccount(@PathVariable Long id) {
        accountLifecycleService.softDeleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    // Restaura uma conta previamente deletada (e efeitos colaterais associados).
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('CP_TENANT_RESUME')")
    public ResponseEntity<Void> restoreAccount(@PathVariable Long id) {
        accountLifecycleService.restoreAccount(id);
        return ResponseEntity.noContent().build();
    }
}
