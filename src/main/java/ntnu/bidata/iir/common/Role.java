package ntnu.bidata.iir.common;

import java.util.Set;

/**
 * Access levels of a control panel. Decides which actuators a panel may command.
 * Viewing sensor data and actuator status is allowed for every role.
 *
 * ADMIN and OWNER: everything.
 * TECHNICIAN: everything except locks and doors (diagnostics, but cannot unlock).
 * GUEST: only the harmless actuators listed in GUEST_ACTUATORS.
 */
public enum Role {
    ADMIN, OWNER, TECHNICIAN, GUEST;

    private static final Set<String> RESTRICTED_ACTUATORS = Set.of("door", "lock");
    private static final Set<String> GUEST_ACTUATORS = Set.of("fan", "light");

    /** @param actuatorId for example "fan:1". */
    public boolean mayControl(String actuatorId) {
        String type = actuatorId.split(":", 2)[0];
        return switch (this) {
            case ADMIN, OWNER -> true;
            case TECHNICIAN -> !RESTRICTED_ACTUATORS.contains(type);
            case GUEST -> GUEST_ACTUATORS.contains(type);
        };
    }

    /** Case-insensitive parse; throws ProtocolException for an unknown role. */
    public static Role parse(String text) {
        try {
            return valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Unknown role: " + text);
        }
    }
}
