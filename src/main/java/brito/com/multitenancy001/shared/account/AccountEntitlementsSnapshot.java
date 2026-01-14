package brito.com.multitenancy001.shared.account;

public record AccountEntitlementsSnapshot(
        int maxUsers,
        int maxProducts,
        int maxStorageMb,
        boolean unlimited
) {

    public static AccountEntitlementsSnapshot ofUnlimited() {
        return new AccountEntitlementsSnapshot(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                true
        );
    }

    public static AccountEntitlementsSnapshot ofLimited(int maxUsers, int maxProducts, int maxStorageMb) {
        return new AccountEntitlementsSnapshot(maxUsers, maxProducts, maxStorageMb, false);
    }
}
