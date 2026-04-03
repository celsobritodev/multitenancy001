package brito.com.multitenancy001.tenant.auth.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.persistence.publicschema.AccountResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por resolver a conta selecionada
 * no fluxo de confirmação de login de tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver conta ativa por accountId.</li>
 *   <li>Resolver conta ativa por slug.</li>
 *   <li>Centralizar a regra de escolha entre accountId e slug.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantLoginSelectionResolver {

    private final AccountResolver accountResolver;

    /**
     * Resolve a conta ativa escolhida no fluxo de confirmação.
     *
     * @param accountId id da conta, quando informado
     * @param slug slug da conta, quando informado
     * @return snapshot da conta ativa ou null
     */
    public AccountSnapshot resolveSelectedAccount(Long accountId, String slug) {
        if (accountId != null) {
            log.debug("Resolvendo conta por accountId={}", accountId);
            return accountResolver.resolveActiveAccountById(accountId);
        }

        log.debug("Resolvendo conta por slug={}", slug);
        return accountResolver.resolveActiveAccountBySlug(slug);
    }
}