import java.io.*;
import java.net.*;

public class Client extends Thread {
    private final InetAddress group;
    private final DatagramSocket unicastSocket;
    private final InetAddress leaderAddress;
    private final int leaderPort;
    private static final int TCP_PORT = 12345;
    private boolean logFileReceived = false; // Flag to track if the log file has been received

    public Client(InetAddress group, int receivePort, InetAddress leaderAddress, int leaderPort) throws IOException {
        this.group = group;
        this.unicastSocket = new DatagramSocket(receivePort);
        this.leaderAddress = leaderAddress;
        this.leaderPort = leaderPort;
        System.out.println("Client initialized on port " + receivePort);
    }

    private void sendJoin() throws IOException {
        String joinMsg = Constants.JOIN_MESSAGE;
        byte[] buffer = joinMsg.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, leaderAddress, leaderPort);
        unicastSocket.send(packet);
        System.out.println("JOIN message sent to leader.");
    }

    private void receiveMessages() {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            unicastSocket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength()).trim();
            System.out.println("Message received from server: " + message);

            if ("ACK_JOIN".equals(message)) {
                System.out.println("Successfully joined the server.");
                startTcpClient(packet.getAddress());
            }
        } catch (IOException e) {
            System.err.println("Error receiving messages: " + e.getMessage());
        }
    }

    private void startTcpClient(InetAddress serverAddress) {
        try (Socket socket = new Socket(serverAddress, TCP_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            System.out.println("Connected to server via TCP.");
            int lineCount = 0;
            while (reader.readLine() != null) {
                lineCount++;
            }
            System.out.println("Log file received from server with " + lineCount + " lines.");
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            sendJoin();
            while (true) {
                receiveMessages();
            }
        } catch (IOException e) {
            System.err.println("Error in client: " + e.getMessage());
        }
    }
}