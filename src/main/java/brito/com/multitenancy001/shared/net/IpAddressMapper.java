package brito.com.multitenancy001.shared.net;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;

@Component
public class IpAddressMapper {

    public InetAddress toInetAddressOrNull(String raw) {
        if (!StringUtils.hasText(raw)) return null;

        // Tomcat às vezes entrega "0:0:0:0:0:0:0:1" (IPv6 localhost)
        String v = raw.trim();

        // Se vier com porta tipo "127.0.0.1:12345" (depende de proxy/header), remove porta.
        int colon = v.lastIndexOf(':');
        if (colon > -1 && v.indexOf('.') > -1) { // heurística simples p/ IPv4 com porta
            String maybePort = v.substring(colon + 1);
            if (maybePort.chars().allMatch(Character::isDigit)) {
                v = v.substring(0, colon);
            }
        }

        try {
            return InetAddress.getByName(v);
        } catch (Exception ignored) {
            return null; // não deixa auditoria derrubar login
        }
    }
}
