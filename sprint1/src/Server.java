import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.Map;

public class Server extends Thread {
    private final MulticastSocket socket;
    private final InetAddress group;
    private final int sendPort;
    private final int receivePort;
    private int ackCount = 0;
    private boolean awaitingAcks = false;
    private boolean committed = false;
    private final Map<String, Integer> arpTable = new HashMap<>(); // ARP table to store client addresses and ports
    private int documentVersion = 1; // Tracks document version

    public Server(InetAddress group, int sendPort, int receivePort) throws IOException {
        this.socket = new MulticastSocket(receivePort);
        this.group = group;
        this.sendPort = sendPort;
        this.receivePort = receivePort;
        socket.joinGroup(group); // Join the multicast group

        System.out.println("Server iniciado");
    }

    public synchronized void receiveJoin(String clientAddress, int clientPort) {
        System.out.println("JOIN recebido do endereço: " + clientAddress + " na porta " + clientPort);
        arpTable.put(clientAddress, clientPort); // Add the node to the ARP table
        System.out.println("Número de nós que se juntaram: " + arpTable.size());

        // Send ACK_JOIN message back to the client
        try {
            String ackMsg = "ACK_JOIN";
            byte[] buffer = ackMsg.getBytes();
            DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(clientAddress), clientPort);
            socket.send(ackPacket);
            System.out.println("ACK_JOIN enviado para " + clientAddress);
        } catch (IOException e) {
            System.err.println("Erro ao enviar ACK_JOIN: " + e.getMessage());
        }

        // Send document immediately after receiving a JOIN message
        sendDocument();
    }

    public synchronized void sendDocument() {
        try {
            if (!awaitingAcks) {
                String msg = Constants.DOCUMENT_MESSAGE_PREFIX + documentVersion + ": Nova versão do documento...";
                byte[] buffer = msg.getBytes();
                for (Map.Entry<String, Integer> entry : arpTable.entrySet()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(entry.getKey()), entry.getValue());
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
            byte[] buffer = Constants.COMMIT_MESSAGE.getBytes();
            for (Map.Entry<String, Integer> entry : arpTable.entrySet()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(entry.getKey()), entry.getValue());
                socket.send(packet);
            }
            committed = true;
            System.out.println("Commit enviado para todos os elementos.");
        } catch (IOException e) {
            System.err.println("Erro ao enviar commit: " + e.getMessage());
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
                        int clientPort = Integer.parseInt(parts[1]);
                        receiveJoin(clientAddress, clientPort);
                    } else if (message.startsWith(Constants.REPLY_MESSAGE)) {
                        receiveArpReply();
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao receber pacote: " + e.getMessage());
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
