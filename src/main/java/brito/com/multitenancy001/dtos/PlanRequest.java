package brito.com.multitenancy001.dtos;



public record PlanRequest(
    String plan,
    Integer maxUsers,
    Integer maxProducts,
    Integer maxStorageMb,
    String reason
) {}