package brito.com.multitenancy001.shared.validation; // ou .validation

/**
 * Padrões de validação reutilizáveis em todo o sistema
 */
public final class ValidationPatterns {
    
    // Username: 3-50 caracteres, letras, números, ponto, underscore, hífen
   // public static final String USERNAME_PATTERN = "^[a-zA-Z0-9._-]{3,50}$";
	
	
	// usado pra facilidar no desenvolvimento
	// Username: 3-50 caracteres, APENAS letras (a-z, A-Z) e números (0-9)
	public static final String USERNAME_PATTERN = "^[a-zA-Z0-9._-]{3,50}$";
    
    // Password: mínimo 8 caracteres, pelo menos 1 letra maiúscula, 1 minúscula e 1 número
   // public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";

    
    
 // ⚠️ APENAS PARA TESTE/DEV - NUNCA EM PRODUÇÃO!
    // Password: mínimo 3 caracteres, apenas letras (maiúsculas/minúsculas)
    // Não precisa de números nem caracteres especiais
	  public static final String PASSWORD_PATTERN = "^[a-zA-Z0-9]{3,}$";
    
    
    
    
    
    
    // Email: validação básica (Spring já tem @Email, mas para referência)
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";
    
    // Nome: apenas letras, espaços e alguns caracteres especiais
    public static final String NAME_PATTERN = "^[a-zA-ZÀ-ÿ\\s'-]{2,100}$";
    
    // Telefone: formato brasileiro
    public static final String PHONE_PATTERN = "^(\\(?\\d{2}\\)?)?\\s?\\d{4,5}-?\\d{4}$";
    
    // CNPJ: formato brasileiro
    public static final String CNPJ_PATTERN = "^\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}$";
    
    // CPF: formato brasileiro
    public static final String CPF_PATTERN = "^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$";
    
    // CEP: formato brasileiro
    public static final String CEP_PATTERN = "^\\d{5}-\\d{3}$";
    
    // URL: para avatar_url, website, etc.
    public static final String URL_PATTERN = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})[/\\w .-]*/?$";
    
    // Timezone: formato padrão (America/Sao_Paulo)
    public static final String TIMEZONE_PATTERN = "^[A-Za-z_]+/[A-Za-z_]+$";
    
    // Locale: pt_BR, en_US, etc.
    public static final String LOCALE_PATTERN = "^[a-z]{2}_[A-Z]{2}$";
    
    // Currency: BRL, USD, EUR (3 letras)
    public static final String CURRENCY_PATTERN = "^[A-Z]{3}$";
    
    // Hex color: #FFFFFF ou #FFF
    public static final String COLOR_PATTERN = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$";
    
    // IP Address
    public static final String IP_PATTERN = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    
    // UUID
    public static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    
    // Permissões: UPPER_CASE_WITH_UNDERSCORE
    public static final String PERMISSION_PATTERN = "^[A-Z_]+$";
    
    private ValidationPatterns() {
        // Construtor privado para classe utilitária
        throw new UnsupportedOperationException("Classe utilitária - não instanciável");
    }
    
    /**
     * Valida se um valor corresponde ao padrão
     */
    public static boolean isValid(String value, String pattern) {
        return value != null && value.matches(pattern);
    }
    
    /**
     * Valida username com mensagem descritiva
     */
    public static void validateUsername(String username) {
        if (username == null || !username.matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException(
                "Username deve ter 3-50 caracteres e conter apenas: " +
                "letras (a-z, A-Z), números (0-9), ponto (.), underscore (_), hífen (-)"
            );
        }
    }
    
    /**
     * Valida password com mensagem descritiva
     */
    public static void validatePassword(String password) {
        if (password == null || !password.matches(PASSWORD_PATTERN)) {
            throw new IllegalArgumentException(
                "Senha deve ter pelo menos 8 caracteres contendo: " +
                "1 letra maiúscula, 1 letra minúscula e 1 número"
            );
        }
    }
}