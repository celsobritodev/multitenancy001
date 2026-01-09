package brito.com.multitenancy001.shared.api.error;



import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiEnumErrorResponse {
    private LocalDateTime timestamp;
    private String error;
    private String message;
    private String field;
    private String invalidValue;
    private List<String> allowedValues;
}