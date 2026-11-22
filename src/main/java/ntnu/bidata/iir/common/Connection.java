package ntnu.bidata.iir.common;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Line-based wrapper around a TCP socket that sends and receives Message objects.
 * Used by the broker, sensor nodes and control panels so framing lives in one place.
 */
public class Connection implements Closeable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    public Connection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
    }

    public static Connection connect(String host, int port) throws IOException {
        return new Connection(new Socket(host, port));
    }

    /** Thread-safe: several threads may send on the same connection. */
    public synchronized void send(Message message) {
        out.println(message.serialize());
    }

    /**
     * Blocks until a line arrives. Returns null when the peer closed the connection.
     * Throws ProtocolException for a malformed line (the caller decides how to react).
     */
    public Message receive() throws IOException {
        String line = in.readLine();
        return line == null ? null : Message.parse(line);
    }

    public String remoteAddress() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
