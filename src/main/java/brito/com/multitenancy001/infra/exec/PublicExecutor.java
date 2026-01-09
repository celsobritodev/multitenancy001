package brito.com.multitenancy001.infra.exec;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import brito.com.multitenancy001.infra.multitenancy.TenantSchemaContext;

@Component
public class PublicExecutor {

	public <T> T run(Supplier<T> fn) {
	    TenantSchemaContext.bindTenantSchema("public"); // garante PUBLIC explicitamente
	    try {
	        return fn.get();
	    } finally {
	        TenantSchemaContext.clearTenantSchema();
	    }
	}

	public void run(Runnable fn) {
	    TenantSchemaContext.bindTenantSchema("public");
	    try {
	        fn.run();
	    } finally {
	        TenantSchemaContext.clearTenantSchema();
	    }
	}

}
