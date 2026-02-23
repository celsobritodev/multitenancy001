package brito.com.multitenancy001.controlplane.accounts.app;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

import brito.com.multitenancy001.controlplane.accounts.app.command.CreateAccountCommand;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;

public final class AccountFactory {

    private AccountFactory() {}

   // src/main/java/brito/com/multitenancy001/controlplane/accounts/app/AccountFactory.java
// Atualizar o método newTenantAccount

    public static Account newTenantAccount(CreateAccountCommand cmd) {
        if (cmd == null) throw new IllegalArgumentException("cmd é obrigatório");

        Account account = new Account();

        account.setDisplayName(cmd.displayName());
        account.setLoginEmail(cmd.loginEmail());
        account.setTaxCountryCode(cmd.taxCountryCode());
        account.setTaxIdType(cmd.taxIdType());
        account.setTaxIdNumber(cmd.taxIdNumber());

        String baseSlug = slugify(cmd.displayName());
        account.setSlug(baseSlug + "-" + shortId());

        account.ensureTenantSchema();

        account.setStatus(AccountStatus.PROVISIONING);

        // Valores padrão para campos adicionais
        account.setCountry("Brasil");
        account.setTimezone("America/Sao_Paulo");
        account.setLocale("pt_BR");
        account.setCurrency("BRL");

        return account;
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
