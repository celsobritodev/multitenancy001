package brito.com.multitenancy001.tenant.categories.app.command;

/**
 * Command de criação de Subcategory.
 *
 * Observação:
 * - categoryId vem do path (controller), mas vira dado do Command para o Service.
 */
public record CreateSubcategoryCommand(
        Long categoryId,
        String name
) {}