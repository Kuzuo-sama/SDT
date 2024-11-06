import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class ReceiveHandler extends Thread {
    private final MulticastSocket socket;
    private final InetAddress group;
    private final int port;
    private final Elemento elemento;
    private final InetAddress leaderAddress;
    private final int leaderPort;

    public ReceiveHandler(InetAddress group, int port, Elemento elemento, InetAddress leaderAddress, int leaderPort) throws IOException {
        this.group = group;
        this.port = port;
        this.elemento = elemento;
        this.leaderAddress = leaderAddress;
        this.leaderPort = leaderPort;
        this.socket = new MulticastSocket(port);
        socket.joinGroup(group); // Faz o join do grupo multicast
    }

    public void receiveMessage() {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet); // Recebe o pacote multicast
            String received = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Mensagem recebida: " + received);

            if (received.contains("Doc v2")) {
                sendAck(); // Envia ACK se o documento for encontrado
            } else if (received.equals("COMMIT")) {
                System.out.println("Commit recebido. Nova versão do documento aplicada.");
            }
        } catch (IOException e) {
            System.err.println("Erro ao receber mensagem: " + e.getMessage());
        }
    }

    public void sendAck() {
        try {
            String ackMsg = "ACK";
            byte[] buffer = ackMsg.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, leaderAddress, leaderPort); // Envia ACK para o líder
            socket.send(packet);
            System.out.println("ACK enviado para o líder.");
        } catch (IOException e) {
            System.err.println("Erro ao enviar ACK: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        while (true) {
            receiveMessage(); // Aguarda receber uma nova mensagem
        }
    }
}
