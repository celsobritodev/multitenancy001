package brito.com.multitenancy001.controlplane.application.account;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;

public final class AccountFactory {

    private AccountFactory() {}

    public static Account newTenantAccount(CreateAccountCommand cmd) {
        Account a = new Account();

        a.setDisplayName(cmd.displayName());
        a.setLoginEmail(cmd.loginEmail());
        a.setTaxCountryCode(cmd.taxCountryCode());
        a.setTaxIdType(cmd.taxIdType());
        a.setTaxIdNumber(cmd.taxIdNumber());

        String baseSlug = slugify(cmd.displayName());
        a.setSlug(baseSlug + "-" + shortId());

        String schemaBase = "t_" + slugify(cmd.displayName()).replace("-", "_");
        a.setSchemaName(schemaBase + "_" + shortId());

        a.setStatus(AccountStatus.PROVISIONING);

        return a;
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
