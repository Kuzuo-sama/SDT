import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Server extends Thread {
    private final MulticastSocket socket;
    private final InetAddress group;
    private final int sendPort;
    private final int receivePort;
    private int ackCount = 0;
    private boolean awaitingAcks = false;
    private boolean committed = false;
    private final Map<String, int[]> arpTable = new HashMap<>(); // ARP table to store client addresses and ports
    private int documentVersion = 1; // Tracks document version
    private final Random random = new Random();

    public Server(InetAddress group, int sendPort, int receivePort) throws IOException {
        this.socket = new MulticastSocket(receivePort);
        this.group = group;
        this.sendPort = sendPort;
        this.receivePort = receivePort;
        socket.joinGroup(group); // Join the multicast group

        System.out.println("Server iniciado");
    }

    public synchronized void receiveJoin(String clientAddress, int clientSendPort, int clientReceivePort) {
        System.out.println("JOIN recebido do endereço: " + clientAddress + " nas portas " + clientSendPort + " e " + clientReceivePort);

        // Check for port conflicts
        for (int[] ports : arpTable.values()) {
            if (ports[0] == clientSendPort || ports[1] == clientReceivePort) {
                // Ports are in use, generate new ports
                int newSendPort = sendPort + random.nextInt(1000) + 1;
                int newReceivePort = receivePort + random.nextInt(1000) + 1;
                sendNewPorts(clientAddress, newSendPort, newReceivePort);
                return;
            }
        }

        // No port conflicts, add the node to the ARP table
        arpTable.put(clientAddress, new int[]{clientSendPort, clientReceivePort});
        System.out.println("Número de nós que se juntaram: " + arpTable.size());

        // Send ACK_JOIN message back to the client
        try {
            String ackMsg = clientAddress + ",ACK_JOIN";
            byte[] buffer = ackMsg.getBytes();
            DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(clientAddress), clientReceivePort);
            socket.send(ackPacket);
            System.out.println("ACK_JOIN enviado para " + clientAddress);
        } catch (IOException e) {
            System.err.println("Erro ao enviar ACK_JOIN: " + e.getMessage());
        }

        // Send document immediately after receiving a JOIN message
        sendDocument();

        // Send version check message to all clients
        sendVersionCheck();
    }

    public synchronized void sendNewPorts(String clientAddress, int newSendPort, int newReceivePort) {
        try {
            String newPortsMsg = "NEW_PORTS," + newSendPort + "," + newReceivePort;
            byte[] buffer = newPortsMsg.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(clientAddress), receivePort);
            socket.send(packet);
            System.out.println("Novas portas enviadas para " + clientAddress + ": " + newSendPort + ", " + newReceivePort);
        } catch (IOException e) {
            System.err.println("Erro ao enviar novas portas: " + e.getMessage());
        }
    }

    public synchronized void sendDocument() {
        try {
            if (!awaitingAcks) {
                String msg = Constants.DOCUMENT_MESSAGE_PREFIX + documentVersion + ": Nova versão do documento...";
                for (Map.Entry<String, int[]> entry : arpTable.entrySet()) {
                    String recipient = entry.getKey();
                    String fullMsg = recipient + "," + msg;
                    byte[] buffer = fullMsg.getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(recipient), entry.getValue()[1]);
                    socket.send(packet);
                }
                awaitingAcks = true;
                System.out.println("Documento enviado pelo líder: " + msg);
            }
        } catch (IOException e) {
            System.err.println("Erro ao enviar documento: " + e.getMessage());
        }
    }

    public synchronized void sendCommit() {
        try {
            String msg = Constants.COMMIT_MESSAGE;
            for (Map.Entry<String, int[]> entry : arpTable.entrySet()) {
                String recipient = entry.getKey();
                String fullMsg = recipient + "," + msg;
                byte[] buffer = fullMsg.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(recipient), entry.getValue()[1]);
                socket.send(packet);
            }
            committed = true;
            System.out.println("Commit enviado para todos os elementos.");
        } catch (IOException e) {
            System.err.println("Erro ao enviar commit: " + e.getMessage());
        }
    }

    public synchronized void sendVersionCheck() {
        try {
            String msg = Constants.VERSION_CHECK_MESSAGE + "," + documentVersion;
            for (Map.Entry<String, int[]> entry : arpTable.entrySet()) {
                String recipient = entry.getKey();
                String fullMsg = recipient + "," + msg;
                byte[] buffer = fullMsg.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(recipient), entry.getValue()[1]);
                socket.send(packet);
            }
            System.out.println("Versão enviada para todos os elementos: " + documentVersion);
        } catch (IOException e) {
            System.err.println("Erro ao enviar versão: " + e.getMessage());
        }
    }

    public synchronized void receiveArpReply() {
        ackCount++;
        System.out.println("ARP REPLY recebido. Total de ARP REPLYs: " + ackCount);

        if (!committed) {
            sendCommit();
            awaitingAcks = false;
            ackCount = 0;
            documentVersion++; // Update document version after commit
        }
    }

    @Override
    public void run() {
        while (true) {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                System.out.println("Pacote recebido de " + packet.getAddress().getHostAddress());
                String message = new String(packet.getData(), 0, packet.getLength());

                synchronized (this) {
                    if (message.startsWith(Constants.JOIN_MESSAGE)) {
                        String[] parts = message.split(",");
                        String clientAddress = packet.getAddress().getHostAddress();
                        int clientSendPort = Integer.parseInt(parts[1]);
                        int clientReceivePort = Integer.parseInt(parts[2]);
                        receiveJoin(clientAddress, clientSendPort, clientReceivePort);
                    } else if (message.startsWith(Constants.REPLY_MESSAGE)) {
                        receiveArpReply();
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao receber pacote: " + e.getMessage());
            }
        }
    }
}