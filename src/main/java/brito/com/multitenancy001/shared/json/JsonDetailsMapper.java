package brito.com.multitenancy001.shared.json;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Mapper central para serialização de details de auditoria.
 *
 * <p>Objetivos:</p>
 * <ul>
 *   <li>Padronizar serialização JSON em um único ponto.</li>
 *   <li>Evitar concatenação manual de string JSON.</li>
 *   <li>Dar suporte uniforme para Map, record, DTO simples e valores escalares.</li>
 * </ul>
 */
@Component
public class JsonDetailsMapper {

    private final ObjectMapper objectMapper;

    /**
     * Construtor padrão.
     *
     * @param objectMapper mapper Jackson da aplicação
     */
    public JsonDetailsMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converte um objeto qualquer em {@link JsonNode}.
     *
     * @param value valor de entrada
     * @return json node correspondente ou null quando value for null
     */
    public JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.valueToTree(value);
    }

    /**
     * Converte um objeto qualquer em string JSON.
     *
     * @param value valor de entrada
     * @return string json ou null quando value for null
     */
    public String toJson(Object value) {
        JsonNode node = toJsonNode(value);
        return node == null ? null : node.toString();
    }

    /**
     * Converte mapa em string JSON.
     *
     * @param details mapa estruturado
     * @return string json ou null
     */
    public String toJson(Map<String, Object> details) {
        return toJson((Object) details);
    }
}