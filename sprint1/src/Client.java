import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Client extends Thread {
    private final MulticastSocket socket;
    private final InetAddress group;
    private int sendPort;
    private int receivePort;
    private final InetAddress leaderAddress;
    private final int leaderPort;
    private boolean joined = false;
    private int documentVersion = 1; // Tracks document version

    public Client(InetAddress group, int sendPort, int receivePort, InetAddress leaderAddress, int leaderPort) throws IOException {
        this.group = group;
        this.sendPort = sendPort;
        this.receivePort = receivePort;
        this.leaderAddress = leaderAddress;
        this.leaderPort = leaderPort;
        this.socket = new MulticastSocket(receivePort);
        socket.joinGroup(group);

        System.out.println("Client iniciado na porta " + receivePort);
        sendJoin();
    }

    public synchronized void sendJoin() {
        try {
            String joinMsg = Constants.JOIN_MESSAGE + "," + sendPort + "," + receivePort;
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

    public synchronized void sendVersionReply() {
        try {
            String versionReply = Constants.VERSION_CHECK_MESSAGE + "," + documentVersion;
            byte[] buffer = versionReply.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, leaderAddress, leaderPort);
            socket.send(packet);
            System.out.println("Versão enviada para o líder: " + documentVersion);
        } catch (IOException e) {
            System.err.println("Erro ao enviar versão: " + e.getMessage());
        }
    }

    public synchronized void receiveMessage() {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength()).trim();
            System.out.println("Mensagem recebida: " + received);

            String[] parts = received.split(",", 2);
            if (parts.length < 2) {
                System.err.println("Mensagem malformada recebida: " + received);
                return;
            }

            String recipient = parts[0];
            String message = parts[1];

            if (!recipient.equals(InetAddress.getLocalHost().getHostAddress())) {
                return; // Ignore messages not meant for this client
            }

            if ("ACK_JOIN".equals(message.trim())) {
                System.out.println("ACK_JOIN recebido do líder. Conexão com o grupo confirmada.");
            } else if (message.startsWith(Constants.DOCUMENT_MESSAGE_PREFIX)) {
                System.out.println("Documento recebido: " + message);
                documentVersion++; // Update document version
                sendArpReply(packet.getAddress().getHostAddress());
            } else if (message.equals(Constants.COMMIT_MESSAGE)) {
                System.out.println("Commit recebido. Nova versão do documento aplicada.");
            } else if (message.startsWith(Constants.VERSION_CHECK_MESSAGE)) {
                System.out.println("Versão recebida do líder: " + message);
                sendVersionReply();
            } else if (message.startsWith("NEW_PORTS")) {
                String[] newPorts = message.split(",");
                sendPort = Integer.parseInt(newPorts[1]);
                receivePort = Integer.parseInt(newPorts[2]);
                System.out.println("Novas portas recebidas do líder: " + sendPort + ", " + receivePort);
                sendJoin(); // Send a new JOIN message with the updated ports
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
