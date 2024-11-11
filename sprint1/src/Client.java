import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Client extends Thread {
    private final MulticastSocket socket;
    private final InetAddress group;
    private final int sendPort;
    private final int receivePort;
    private final InetAddress leaderAddress;
    private final int leaderPort;
    private boolean joined = false;

    public Client(InetAddress group, int sendPort, int receivePort, InetAddress leaderAddress, int leaderPort) throws IOException {
        this.group = group;
        this.sendPort = sendPort;
        this.receivePort = receivePort;
        this.leaderAddress = leaderAddress;
        this.leaderPort = leaderPort;
        this.socket = new MulticastSocket(receivePort);
        socket.joinGroup(group);

        System.out.println("Client iniciado");
        sendJoin();
    }

    public synchronized void sendJoin() {
        try {
            String joinMsg = Constants.JOIN_MESSAGE + "," + receivePort;
            byte[] buffer = joinMsg.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, leaderAddress, leaderPort);
            socket.send(packet);
            joined = true;
            notifyAll();
            System.out.println("JOIN enviado para o líder.");
        } catch (IOException e) {
            System.err.println("Erro ao enviar JOIN: " + e.getMessage());
        }
    }

    public synchronized void sendArpReply(String targetIp) {
        try {
            String replyMessage = Constants.REPLY_MESSAGE + "," + InetAddress.getLocalHost().getHostAddress() + "," + targetIp;
            byte[] buffer = replyMessage.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, leaderAddress, leaderPort);
            socket.send(packet);
            System.out.println("ARP REPLY enviado para o líder.");
        } catch (IOException e) {
            System.err.println("Erro ao enviar ARP REPLY: " + e.getMessage());
        }
    }

    public synchronized void receiveMessage() {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength()).trim();
            System.out.println("Mensagem recebida: " + received);

            if ("ACK_JOIN".equals(received.trim())) {
                System.out.println("ACK_JOIN recebido do líder. Conexão com o grupo confirmada.");
            } else if (received.startsWith(Constants.DOCUMENT_MESSAGE_PREFIX)) {
                System.out.println("Documento recebido: " + received);
                sendArpReply(packet.getAddress().getHostAddress());
            } else if (received.equals(Constants.COMMIT_MESSAGE)) {
                System.out.println("Commit recebido. Nova versão do documento aplicada.");
            }
        } catch (IOException e) {
            System.err.println("Erro ao receber mensagem: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        while (!joined) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        while (true) {
            receiveMessage();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
