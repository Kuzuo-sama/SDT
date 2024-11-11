import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

public class HeartbeatSystem {
    private static final AtomicInteger clientCounter = new AtomicInteger(0);
    private static final Random random = new Random();

    public static void main(String[] args) {
        boolean isLeader = args.length > 0 && args[0].equals("leader");

        try {
            InetAddress group = InetAddress.getByName(Constants.MULTICAST_GROUP);
            int sendPort = Constants.SEND_PORT;
            int receivePort = Constants.RECEIVE_PORT;

            System.out.println("Iniciando HeartbeatSystem");
            System.out.println("Liderança: " + (isLeader ? "Sim" : "Não"));
            System.out.println("Endereço do grupo: " + group.getHostAddress());
            System.out.println("Porta de envio: " + sendPort);
            System.out.println("Porta de recebimento: " + receivePort);

            if (isLeader) {
                Server server = new Server(group, sendPort, receivePort);
                server.start();
            } else {
                InetAddress leaderAddress = InetAddress.getByName(Constants.MULTICAST_GROUP);

                // Generate unique send and receive ports for each client
                int clientIndex = clientCounter.incrementAndGet();
                int clientSendPort = sendPort + clientIndex + random.nextInt(1000);
                int clientReceivePort = receivePort + clientIndex + random.nextInt(1000);

                Client client = new Client(group, clientSendPort, clientReceivePort, leaderAddress, receivePort);
                client.start();
            }

        } catch (IOException e) {
            System.err.println("Erro no sistema de heartbeat: " + e.getMessage());
        }
    }
}
