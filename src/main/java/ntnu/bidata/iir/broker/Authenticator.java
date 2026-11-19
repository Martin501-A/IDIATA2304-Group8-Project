package ntnu.bidata.iir.broker;

import java.util.Map;
import ntnu.bidata.iir.common.Role;

/**
 * Checks the password a panel sends at login.
 * Simplification: one fixed password per role, sent in plain text.
 * (Real systems would use per-user accounts, hashing and TLS; see "Possible extras".)
 */

final class Authenticator {
    private static final Map<Role, String> PASSWORDS = Map.of(
            Role.ADMIN, "admin123",
            Role.OWNER, "owner123",
            Role.TECHNICIAN, "tech123",
            Role.GUEST, "guest123");

    private Authenticator() { }

    static boolean isValid(Role role, String password) {
        return PASSWORDS.get(role).equals(password);
    }
}
