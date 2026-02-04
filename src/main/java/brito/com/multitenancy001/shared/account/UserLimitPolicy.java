package brito.com.multitenancy001.shared.account;

public enum UserLimitPolicy {
    SEATS_IN_USE,        // deleted=false
    SEATS_ENABLED,
    ACTIVE_USERS_ONLY    // deleted=false AND suspendedByAccount=false AND suspendedByAdmin=false
}

