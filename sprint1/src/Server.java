import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Set;

public class Server extends Thread {
    private final MulticastSocket socket;
    private final InetAddress group;
    private final int sendPort;
    private final int receivePort;
    private final Set<InetAddress> clients = new HashSet<>();
    private BufferedWriter logWriter;
    private static final int TCP_PORT = 12345;
    private static final int HEARTBEAT_INTERVAL = 2000; // 2 seconds

    public Server(InetAddress group, int sendPort, int receivePort) throws IOException {
        this.group = group;
        this.sendPort = sendPort;
        this.receivePort = receivePort;
        this.socket = new MulticastSocket(receivePort);
        socket.joinGroup(new InetSocketAddress(group, receivePort), null);

        // Initialize log writer
        logWriter = new BufferedWriter(new FileWriter("server_log.txt", true));
        log("Server initialized as leader.");

        // Start TCP server in a separate thread
        new Thread(this::startTcpServer).start();

        // Start heartbeat updates
        startHeartbeatUpdates();
    }

    private void log(String message) {
        try {
            logWriter.write(message);
            logWriter.newLine();
            logWriter.flush();
            System.out.println(message);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    // Start TCP server for sending log files
    private void startTcpServer() {
        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            System.out.println("TCP server started on port " + TCP_PORT);

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connected for log file transfer");

                    // Send log file to client
                    sendLogFile(clientSocket);
                } catch (IOException e) {
                    System.err.println("Erro ao aceitar conexão do cliente: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao iniciar o servidor TCP: " + e.getMessage());
        }
    }

    // Send the log file to the client using TCP
    private void sendLogFile(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(new FileReader("server_log.txt"));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String line;
            while ((line = reader.readLine()) != null) {
                writer.println(line);
            }
            System.out.println("Log file sent to client");
        } catch (FileNotFoundException e) {
            System.err.println("Erro ao enviar o arquivo de log: Arquivo não encontrado");
        } catch (IOException e) {
            System.err.println("Erro ao enviar o arquivo de log: " + e.getMessage());
        }
    }

    private void receiveClientMessages() {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength()).trim();
            log("Message received: " + received);

            if (received.startsWith(Constants.JOIN_MESSAGE)) {
                clients.add(packet.getAddress());
                sendAck(packet); // Send ACK but don't immediately send the log file
            } else if (received.startsWith("ACK_LOG")) {
                log("Log file acknowledged by client: " + packet.getAddress().getHostAddress());
                // Acknowledge without triggering an immediate log transfer
            }
        } catch (IOException e) {
            log("Error receiving client message: " + e.getMessage());
        }
    }

    private void sendAck(DatagramPacket packet) throws IOException {
        String ackMessage = "ACK_JOIN";
        byte[] ackBuffer = ackMessage.getBytes();
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, packet.getAddress(), packet.getPort());
        socket.send(ackPacket);
        log("ACK_JOIN sent to client.");
    }

    private void sendHeartbeat() {
        try {
            String heartbeatMessage = Constants.HEARTBEAT_MESSAGE;
            byte[] buffer = heartbeatMessage.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, sendPort);
            socket.send(packet);
            log("Heartbeat message sent.");
        } catch (IOException e) {
            log("Error sending heartbeat: " + e.getMessage());
        }
    }

    private void startHeartbeatUpdates() {
        new Thread(() -> {
            while (true) {
                sendHeartbeat();
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    @Override
    public void run() {
        log("Server running, waiting for clients...");
        while (true) {
            receiveClientMessages();
        }
    }
}