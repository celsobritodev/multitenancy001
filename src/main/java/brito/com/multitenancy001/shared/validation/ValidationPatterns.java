package brito.com.multitenancy001.shared.validation;

/**
 * Padrões de validação reutilizáveis em todo o sistema
 */
public final class ValidationPatterns {

    // ⚠️ APENAS PARA TESTE/DEV - NUNCA EM PRODUÇÃO!
    // Password: mínimo 3 caracteres, letras/números
    public static final String PASSWORD_PATTERN = "^[a-zA-Z0-9]{3,}$";

    // Email
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";

    // Nome
    public static final String NAME_PATTERN = "^[a-zA-ZÀ-ÿ\\s'-]{2,100}$";

    // Telefone
    public static final String PHONE_PATTERN = "^(\\(?\\d{2}\\)?)?\\s?\\d{4,5}-?\\d{4}$";

    public static final String CNPJ_PATTERN = "^\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}$";
    public static final String CPF_PATTERN  = "^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$";
    public static final String CEP_PATTERN  = "^\\d{5}-\\d{3}$";

    public static final String URL_PATTERN = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})[/\\w .-]*/?$";

    public static final String TIMEZONE_PATTERN = "^[A-Za-z_]+/[A-Za-z_]+$";
    public static final String LOCALE_PATTERN   = "^[a-z]{2}_[A-Z]{2}$";
    public static final String CURRENCY_PATTERN = "^[A-Z]{3}$";
    public static final String COLOR_PATTERN    = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$";
    public static final String IP_PATTERN       = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    public static final String UUID_PATTERN     = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    public static final String PERMISSION_PATTERN = "^[A-Z_]+$";

    private ValidationPatterns() {
        throw new UnsupportedOperationException("Classe utilitária - não instanciável");
    }

    public static boolean isValid(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    public static void validatePassword(String password) {
        if (password == null || !password.matches(PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("Senha inválida");
        }
    }
}

