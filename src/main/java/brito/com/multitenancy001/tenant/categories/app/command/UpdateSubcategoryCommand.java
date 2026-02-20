package brito.com.multitenancy001.tenant.categories.app.command;

/**
 * Command de atualização de Subcategory.
 */
public record UpdateSubcategoryCommand(
        String name
) {}