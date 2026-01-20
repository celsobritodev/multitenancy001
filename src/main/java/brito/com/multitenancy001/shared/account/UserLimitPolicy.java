package brito.com.multitenancy001.shared.account;

public enum UserLimitPolicy {
    SEATS_IN_USE,        // deleted=false
    ACTIVE_USERS_ONLY    // deleted=false AND suspendedByAccount=false AND suspendedByAdmin=false
}
