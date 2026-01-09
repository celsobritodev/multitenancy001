package brito.com.multitenancy001.shared.api.error;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ApiErrorResponse {

    private LocalDateTime timestamp;
    private String error;
    private String message;

    // 🔥 novo campo (opcional)
    private List<String> details;
}
