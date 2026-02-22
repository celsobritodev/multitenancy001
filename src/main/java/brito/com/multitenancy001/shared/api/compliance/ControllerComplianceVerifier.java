package brito.com.multitenancy001.shared.api.compliance;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.repository.Repository;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Verificador automático de compliance arquitetural de Controllers.
 *
 * Objetivo:
 * - Garantir que Controllers sigam regras mínimas de arquitetura e DDD.
 *
 * Regras verificadas:
 * - Controller não pode acessar Repository diretamente.
 * - Controller deve expor DTOs (request/response), não entidades.
 * - Controller deve depender apenas de Application Services.
 *
 * Papel arquitetural:
 * - Mecanismo de "fail fast" durante startup.
 * - Evita regressões arquiteturais silenciosas ao longo do tempo.
 *
 * Importante:
 * - Controllers explicitamente excepcionais devem usar @ControllerComplianceExempt.
 * - Este verificador protege a integridade do projeto a longo prazo.
 */
@Component
@RequiredArgsConstructor
public class ControllerComplianceVerifier implements ApplicationRunner {

    private final ApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        /* Comentário do método: executa o scan de controllers e falha o startup caso encontre violações. */

        List<String> violations = new ArrayList<>();

        Map<String, Object> controllers = findControllerBeans();

        for (Map.Entry<String, Object> entry : controllers.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();

            Class<?> beanClass = AopUtils.getTargetClass(bean);
            if (beanClass == null) {
                continue;
            }

            if (isExempt(beanClass)) {
                continue;
            }

            violations.addAll(checkControllerBean(beanName, beanClass));
        }

        if (!violations.isEmpty()) {
            String message = buildFailFastMessage(violations);
            throw new IllegalStateException(message);
        }
    }

    private Map<String, Object> findControllerBeans() {
        /* Comentário do método: coleta todos os beans anotados com @RestController ou @Controller. */

        Map<String, Object> rest = applicationContext.getBeansWithAnnotation(RestController.class);
        Map<String, Object> mvc = applicationContext.getBeansWithAnnotation(Controller.class);

        // Merge (rest tem prioridade)
        Map<String, Object> all = new LinkedHashMap<>();
        all.putAll(mvc);
        all.putAll(rest);
        return all;
    }

    private List<String> checkControllerBean(String beanName, Class<?> controllerClass) {
        /* Comentário do método: aplica todas as regras de compliance para o controller alvo. */

        List<String> v = new ArrayList<>();

        // 1) Proibidos no controller: Repository, EntityManager, JdbcTemplate
        v.addAll(checkForbiddenDependencies(beanName, controllerClass));

        // 2) @Transactional no controller
        v.addAll(checkTransactionalUsage(beanName, controllerClass));

        // 3) @RequestBody sem @Valid
        v.addAll(checkRequestBodyValidation(beanName, controllerClass));

        // 4) Retornando entidades JPA / Optional
        v.addAll(checkReturnTypes(beanName, controllerClass));

        return v;
    }

    private List<String> checkForbiddenDependencies(String beanName, Class<?> controllerClass) {
        /* Comentário do método: detecta campos injetados proibidos (repo/entityManager/jdbc). */

        List<String> v = new ArrayList<>();

        for (Field f : getAllFields(controllerClass)) {
            Class<?> type = f.getType();

            // ignora constantes
            if (Modifier.isStatic(f.getModifiers())) continue;

            if (isExempt(f)) continue;

            if (Repository.class.isAssignableFrom(type) || type.getSimpleName().endsWith("Repository")) {
                v.add(fmt(beanName, controllerClass,
                        "FORBIDDEN_DEPENDENCY",
                        "Controller injeta repository direto: field '" + f.getName() + "' type=" + type.getName()));
            }

            if (EntityManager.class.isAssignableFrom(type)) {
                v.add(fmt(beanName, controllerClass,
                        "FORBIDDEN_DEPENDENCY",
                        "Controller injeta EntityManager: field '" + f.getName() + "'"));
            }

            if (JdbcTemplate.class.isAssignableFrom(type) || type.getSimpleName().endsWith("JdbcTemplate")) {
                v.add(fmt(beanName, controllerClass,
                        "FORBIDDEN_DEPENDENCY",
                        "Controller injeta JdbcTemplate: field '" + f.getName() + "'"));
            }
        }

        return v;
    }

    private List<String> checkTransactionalUsage(String beanName, Class<?> controllerClass) {
        /* Comentário do método: impede @Transactional em classe ou métodos do controller. */

        List<String> v = new ArrayList<>();

        if (hasAnnotation(controllerClass, Transactional.class)) {
            v.add(fmt(beanName, controllerClass,
                    "TRANSACTIONAL_IN_CONTROLLER",
                    "@Transactional anotado no controller (classe)"));
        }

        for (Method m : getAllMethods(controllerClass)) {
            if (isExempt(m)) continue;

            if (hasAnnotation(m, Transactional.class)) {
                v.add(fmt(beanName, controllerClass,
                        "TRANSACTIONAL_IN_CONTROLLER",
                        "@Transactional anotado no controller (método): " + signature(m)));
            }
        }

        return v;
    }

    private List<String> checkRequestBodyValidation(String beanName, Class<?> controllerClass) {
        /* Comentário do método: garante @Valid junto de @RequestBody (principalmente em create/update). */

        List<String> v = new ArrayList<>();

        for (Method m : getAllMethods(controllerClass)) {
            if (isExempt(m)) continue;

            Parameter[] params = m.getParameters();
            for (Parameter p : params) {

                if (!hasAnnotation(p, RequestBody.class)) continue;

                boolean hasValid = hasAnnotation(p, Valid.class);
                if (!hasValid) {
                    v.add(fmt(beanName, controllerClass,
                            "MISSING_VALIDATION",
                            "Parâmetro @RequestBody sem @Valid em " + signature(m) +
                                    " param='" + p.getName() + "' type=" + p.getType().getName()));
                }
            }
        }

        return v;
    }

    private List<String> checkReturnTypes(String beanName, Class<?> controllerClass) {
        /* Comentário do método: impede retorno de @Entity/Optional e detecta ResponseEntity<Entity>. */

        List<String> v = new ArrayList<>();

        for (Method m : getAllMethods(controllerClass)) {
            if (isExempt(m)) continue;

            Class<?> rawReturn = m.getReturnType();

            // Optional direto
            if (Optional.class.isAssignableFrom(rawReturn)) {
                v.add(fmt(beanName, controllerClass,
                        "OPTIONAL_RETURN",
                        "Método retorna Optional diretamente (anti-pattern): " + signature(m)));
                continue;
            }

            // Entidade direta
            if (isJpaEntity(rawReturn)) {
                v.add(fmt(beanName, controllerClass,
                        "ENTITY_EXPOSED",
                        "Método retorna @Entity diretamente: " + signature(m) + " return=" + rawReturn.getName()));
                continue;
            }

            // ResponseEntity<T> onde T é @Entity
            if (ResponseEntity.class.isAssignableFrom(rawReturn)) {
                Type generic = m.getGenericReturnType();
                Type inner = extractSingleGenericArg(generic);
                if (inner instanceof Class<?> innerClass && isJpaEntity(innerClass)) {
                    v.add(fmt(beanName, controllerClass,
                            "ENTITY_EXPOSED",
                            "Método retorna ResponseEntity<@Entity>: " + signature(m) + " inner=" + innerClass.getName()));
                }
            }
        }

        return v;
    }

    private static boolean isJpaEntity(Class<?> type) {
        /* Comentário do método: verifica se o tipo é anotado com @Entity. */
        if (type == null) return false;
        return hasAnnotation(type, Entity.class);
    }

    private static Type extractSingleGenericArg(Type type) {
        /* Comentário do método: extrai o T de ResponseEntity<T> ou retorna null. */
        if (!(type instanceof ParameterizedType pt)) return null;
        Type[] args = pt.getActualTypeArguments();
        if (args == null || args.length != 1) return null;
        return args[0];
    }

    private static List<Field> getAllFields(Class<?> type) {
        /* Comentário do método: retorna todos os campos herdados + declarados. */
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    private static List<Method> getAllMethods(Class<?> type) {
        /* Comentário do método: retorna métodos declarados + herdados (exceto Object). */
        Map<String, Method> unique = new LinkedHashMap<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                unique.putIfAbsent(methodKey(m), m);
            }
            current = current.getSuperclass();
        }
        return new ArrayList<>(unique.values());
    }

    private static String methodKey(Method m) {
        /* Comentário do método: cria chave única por assinatura. */
        String params = Arrays.stream(m.getParameterTypes()).map(Class::getName).collect(Collectors.joining(","));
        return m.getName() + "(" + params + ")";
    }

    private static String signature(Method m) {
        /* Comentário do método: formata uma assinatura humana do método. */
        String params = Arrays.stream(m.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return m.getDeclaringClass().getSimpleName() + "#" + m.getName() + "(" + params + ")";
    }

    private static boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> ann) {
        /* Comentário do método: checa anotação considerando meta-annotations (sem raw types). */
        return AnnotatedElementUtils.hasAnnotation(element, ann);
    }

    private static boolean isExempt(AnnotatedElement element) {
        /* Comentário do método: ignora itens anotados com @ControllerComplianceExempt. */
        return hasAnnotation(element, ControllerComplianceExempt.class);
    }

    private static String fmt(String beanName, Class<?> controllerClass, String code, String msg) {
        /* Comentário do método: padroniza mensagens para facilitar leitura no fail-fast. */
        return "[" + code + "] bean=" + beanName + " controller=" + controllerClass.getName() + " :: " + msg;
    }

    private static String buildFailFastMessage(List<String> violations) {
        /* Comentário do método: monta mensagem final (agrupada) para exception de startup. */
        StringBuilder sb = new StringBuilder(4096);
        sb.append("\n");
        sb.append("============================================================\n");
        sb.append("CONTROLLER COMPLIANCE VERIFIER - FAIL FAST\n");
        sb.append("============================================================\n");
        sb.append("Foram encontradas violações de arquitetura em Controllers.\n");
        sb.append("Corrija antes de prosseguir.\n\n");

        // agrupa por controller para ficar fácil
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String v : violations) {
            int idx = v.indexOf("controller=");
            int end = v.indexOf(" :: ");
            String key = (idx >= 0 && end > idx) ? v.substring(idx, end) : "controller=UNKNOWN";
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }

        for (Map.Entry<String, List<String>> e : grouped.entrySet()) {
            sb.append("- ").append(e.getKey()).append("\n");
            for (String line : e.getValue()) {
                sb.append("  - ").append(line).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Regras principais:\n");
        sb.append("  - Controller -> AppService (nunca Repository/EntityManager/JdbcTemplate)\n");
        sb.append("  - Sem @Transactional em Controllers\n");
        sb.append("  - @RequestBody deve ter @Valid\n");
        sb.append("  - API não expõe @Entity diretamente\n");
        sb.append("============================================================\n");
        return sb.toString();
    }
}