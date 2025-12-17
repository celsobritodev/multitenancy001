package brito.com.multitenancy001.configuration;




public class TenantContext {
	
	
	
	private static final ThreadLocal<String> CURRENT_TENANT =
	        ThreadLocal.withInitial(() -> "public");


public static String getCurrentTenant() {
return CURRENT_TENANT.get();
}


public static void setCurrentTenant(String tenantId) {
CURRENT_TENANT.set(tenantId);
}


public static void clear() {
CURRENT_TENANT.remove();
}
}