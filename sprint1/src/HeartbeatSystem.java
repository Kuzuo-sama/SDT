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
            int baseSendPort = Constants.SEND_PORT;
            int baseReceivePort = Constants.RECEIVE_PORT;

            System.out.println("Iniciando HeartbeatSystem");
            System.out.println("Liderança: " + (isLeader ? "Sim" : "Não"));
            System.out.println("Endereço do grupo: " + group.getHostAddress());
            System.out.println("Porta base de envio: " + baseSendPort);
            System.out.println("Porta base de recebimento: " + baseReceivePort);

            if (isLeader) {
                // Inicia o servidor como líder
                Server server = new Server(group, baseSendPort, baseReceivePort); // Agora o servidor também precisa da porta de recebimento
                server.start();
            } else {
                // Define o endereço do líder como o endereço do grupo multicast
                InetAddress leaderAddress = InetAddress.getByName(Constants.MULTICAST_GROUP);

                // Gera portas únicas para o cliente
                int clientIndex = clientCounter.incrementAndGet();
                int clientSendPort = baseSendPort + clientIndex + random.nextInt(1000);
                int clientReceivePort = baseReceivePort + clientIndex + random.nextInt(1000);

                System.out.println("Criando cliente com as seguintes portas:");
                System.out.println("Porta de envio: " + clientSendPort);
                System.out.println("Porta de recebimento: " + clientReceivePort);

                // Inicia o cliente
                Client client = new Client(group, clientReceivePort, leaderAddress, baseReceivePort);
                client.start();
            }

        } catch (IOException e) {
            System.err.println("Erro no sistema de heartbeat: " + e.getMessage());
        }
    }
}
