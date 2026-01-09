package brito.com.multitenancy001.infrastructure.exec;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.context.TenantContext;

@Component
public class PublicExecutor {

	public <T> T run(Supplier<T> fn) {
	    TenantContext.bind("public"); // garante PUBLIC explicitamente
	    try {
	        return fn.get();
	    } finally {
	        TenantContext.clear();
	    }
	}

	public void run(Runnable fn) {
	    TenantContext.bind("public");
	    try {
	        fn.run();
	    } finally {
	        TenantContext.clear();
	    }
	}

}
