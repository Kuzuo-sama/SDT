import java.io.IOException;
import java.net.InetAddress;

public class HeartbeatSystem {
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

                Client client = new Client(group, sendPort, receivePort, leaderAddress, receivePort);
                client.start();
            }

        } catch (IOException e) {
            System.err.println("Erro no sistema de heartbeat: " + e.getMessage());
        }
    }
}
