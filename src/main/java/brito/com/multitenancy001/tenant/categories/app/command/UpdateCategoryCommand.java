package brito.com.multitenancy001.tenant.categories.app.command;

/**
 * Command de Application Layer para atualização de Category.
 */
public record UpdateCategoryCommand(
        String name
) {}