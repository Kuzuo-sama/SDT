import java.io.IOException;
import java.net.InetAddress;

public class HeartbeatSystem {
    public static void main(String[] args) {
        // Verifica se o programa é executado como líder
        boolean isLeader = args.length > 0 && args[0].equals("leader");

        Elemento elemento = new Elemento(isLeader);
        try {
            InetAddress group = InetAddress.getByName("230.0.0.0"); // Endereço multicast
            int port = 4446; // Porta para comunicação de pacotes multicast
            int quorum = 2; // Exemplo de quórum para maioria em um grupo de 3

            // Se for o líder, cria e inicia o SendTransmitter
            if (elemento.isLeader()) {
                SendTransmitter transmitter = new SendTransmitter(group, port, elemento, quorum);
                transmitter.start();
            }

            // O endereço do líder deve ser conhecido por todos os elementos
            InetAddress leaderAddress = InetAddress.getByName("230.0.0.0"); // Usando o mesmo grupo multicast
            int leaderPort = 4447; // Porta de ACKs (deve ser a mesma porta de comunicação)

            // Cria e inicia o ReceiveHandler para os elementos
            ReceiveHandler receiver = new ReceiveHandler(group, port, elemento, leaderAddress, leaderPort);
            receiver.start();

        } catch (IOException e) {
            System.err.println("Erro no sistema de heartbeat: " + e.getMessage());
        }
    }
}

