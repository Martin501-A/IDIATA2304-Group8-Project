package ntnu.bidata.iir.common;

/** Constants shared by every node. */
public final class Protocol {
    public static final int PORT = 5050;
    public static final String SEPARATOR = "|";
    public static final String LIST_SEPARATOR = ",";

    public static final String ERR_BAD_REQUEST = "400";
    public static final String ERR_UNAUTHENTICATED = "401";
    public static final String ERR_FORBIDDEN = "403";
    public static final String ERR_NOT_FOUND = "404";
    public static final String ERR_CONFLICT = "409";

    private Protocol() { }
}
