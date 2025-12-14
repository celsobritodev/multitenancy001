package brito.com.multitenancy001.dtos;



public record JwtResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    Long userId,
    String username,
    String email,
    String role,
    Long accountId,
    String tenantSchema
) {
    public JwtResponse {
        if (tokenType == null || tokenType.isEmpty()) {
            tokenType = "Bearer";
        }
    }
    
    // Constructor conveniência
    public JwtResponse(String accessToken, String refreshToken, 
                      Long userId, String username, String email, 
                      String role, Long accountId, String tenantSchema) {
        this(accessToken, refreshToken, "Bearer", 
             userId, username, email, role, accountId, tenantSchema);
    }
}