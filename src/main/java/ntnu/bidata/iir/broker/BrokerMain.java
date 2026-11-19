package ntnu.bidata.iir.broker;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ntnu.bidata.iir.common.Connection;
import ntnu.bidata.iir.common.Protocol;

/** Entry point of the broker. Usage: BrokerMain [port] */

public final class BrokerMain {
    private BrokerMain() { }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.PORT;
        Registry registry = new Registry();

        try (ServerSocket server = new ServerSocket(port);
             ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("Broker listening on port " + port);
            while (true) {
                Connection connection = new Connection(server.accept());
                pool.submit(new ClientHandler(connection, registry));
            }
        }
    }
}
