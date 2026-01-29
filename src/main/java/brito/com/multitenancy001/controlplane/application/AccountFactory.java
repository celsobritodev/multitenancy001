package brito.com.multitenancy001.controlplane.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.domain.account.Account;

/**
 * Factory para criar Account TENANT a partir do SignupRequest (DTO real do seu projeto).
 *
 * - Gera slug e schemaName determinísticos "bonitos" + sufixo aleatório curto.
 * - NÃO seta status aqui (no seu domínio não existe PROVISIONING).
 *   Deixe o PublicAccountCreationService controlar FREE_TRIAL / datas / plano etc.
 */
public final class AccountFactory {

    private AccountFactory() {}

    public static Account newTenantAccountFromSignup(SignupRequest req) {
        Account a = new Account();

        a.setDisplayName(req.displayName());
        a.setLoginEmail(normalizeEmail(req.loginEmail()));

        // Você travou BR no onboarding; manter coerente:
        a.setTaxCountryCode("BR");
        a.setTaxIdType(req.taxIdType());
        a.setTaxIdNumber(req.taxIdNumber());

        String baseSlug = slugify(req.displayName());
        a.setSlug(baseSlug + "-" + shortId());

        String schemaBase = "t_" + slugify(req.displayName()).replace("-", "_");
        a.setSchemaName(schemaBase + "_" + shortId());

        // ⚠️ NÃO setar status aqui. Ex:
        // a.setStatus(AccountStatus.FREE_TRIAL);  <-- deixe no PublicAccountCreationService

        return a;
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private static String slugify(String input) {
        if (input == null || input.isBlank()) return "tenant";
        String n = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String s = n.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");
        return s.isBlank() ? "tenant" : s;
    }
}
