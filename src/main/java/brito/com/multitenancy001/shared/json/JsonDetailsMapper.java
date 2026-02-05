package brito.com.multitenancy001.shared.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonDetailsMapper {

    private final ObjectMapper objectMapper;

    public JsonNode toJsonNode(Object details) {
        if (details == null) return NullNode.getInstance();

        if (details instanceof JsonNode node) {
            return node;
        }

        if (details instanceof String s) {
            if (s.isBlank()) return NullNode.getInstance();
            try {
                return objectMapper.readTree(s); // se for JSON válido, vira JsonNode
            } catch (Exception ignored) {
                return TextNode.valueOf(s); // compat: texto puro vira JSON string
            }
        }

        // Map, DTO, record, etc.
        return objectMapper.valueToTree(details);
    }
}
