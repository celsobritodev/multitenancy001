package brito.com.multitenancy001.exceptions;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final String error;
    private final int status;

    public ApiException(String error, String message, int status) {
        super(message);
        this.error = error;
        this.status = status;
    }
}
