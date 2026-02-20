package brito.com.multitenancy001.tenant.categories.app.command;

/**
 * Command de Application Layer para criação de Category.
 *
 * Regras:
 * - Commands são objetos "puros" (sem annotations HTTP)
 * - Service trabalha com Commands, não com DTOs HTTP
 */
public record CreateCategoryCommand(
        String name
) {}