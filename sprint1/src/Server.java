import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Server extends Thread {
    private final MulticastSocket socket;
    private final InetAddress group;
    private final int sendPort;
     private InetSocketAddress sockets;
    private final int receivePort;
    private final AtomicInteger documentVersion = new AtomicInteger(0); // Versão do documento
    private int connectedClients = 0; // Contador de clientes conectados
    private final Set<InetAddress> clients = new HashSet<>(); // Conjunto de clientes
    private int responsesReceived = 0; // Contador de respostas dos clientes
    private final int totalClients = 3; // Total de clientes esperados

    public Server(InetAddress group, int sendPort, int receivePort) throws IOException {
        this.group = group;
        this.sendPort = sendPort;
        this.receivePort = receivePort;
        this.socket = new MulticastSocket(receivePort);

        sockets = new InetSocketAddress(group, receivePort);
        socket.setTimeToLive(255);

        socket.joinGroup(sockets, null);

        System.out.println("Servidor iniciado como líder.");
    }

    // Envia o heartbeat para todos os clientes
    private void sendHeartbeat() {
        try {
            String heartbeatMessage = Constants.HEARTBEAT_MESSAGE;
            byte[] buffer = heartbeatMessage.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, sendPort);
    
            // Envia o pacote
            socket.send(packet);
    
            // Imprime informações sobre o envio
            System.out.println("Heartbeat enviado para o grupo: " + group.getHostAddress() + ":" + sendPort);
            System.out.println("Conteúdo do Heartbeat: " + heartbeatMessage);
        } catch (IOException e) {
            System.err.println("Erro ao enviar heartbeat: " + e.getMessage());
        }
    }
    

    // Envia o documento para todos os clientes
    private void sendDocumentUpdate() {
        try {
            int newVersion = documentVersion.get();
            if (newVersion >= 0 && newVersion < Constants.DOCUMENT_VERSIONS.size()) {
                String documentMessage = Constants.DOCUMENT_PREFIX + " " + Constants.DOCUMENT_VERSIONS.get(newVersion);
                byte[] buffer = documentMessage.getBytes();
    
                // Envia a mensagem de documento via multicast
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, sendPort);
                socket.send(packet);
                System.out.println("Atualização de documento enviada: " + documentMessage);
    
                responsesReceived = 0; // Resetar contador de respostas após enviar o documento
            } else {
                System.out.println("Não há mais nada a mandar");
            }
        } catch (IOException e) {
            System.err.println("Erro ao enviar atualização de documento: " + e.getMessage());
        }
    }

    // Recebe as mensagens dos clientes
    private void receiveClientMessages() {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength()).trim();
            System.out.println("Mensagem recebida de cliente: " + received);

            // Processar mensagens do cliente
            if (received.startsWith(Constants.JOIN_MESSAGE)) {
                System.out.println("Cliente pediu JOIN. Resposta sendo preparada...");
                String ackMessage = "ACK_JOIN";
                byte[] ackBuffer = ackMessage.getBytes();
                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, packet.getAddress(), packet.getPort());
                socket.send(ackPacket);
                System.out.println("ACK_JOIN enviado para o cliente.");
                connectedClients++;
                clients.add(packet.getAddress()); // Adiciona cliente à lista de clientes conectados

                // Se pelo menos um cliente se conectou, iniciar envio de heartbeats e atualizações
                if (connectedClients == 1) {
                    startHeartbeatAndDocumentUpdates();
                }
            } else if (received.startsWith("DOCUMENT_RECEIVED")) {
                // Processa a confirmação de recebimento do documento
                String[] parts = received.split(",");
                int version = Integer.parseInt(parts[1]);

                if (version == documentVersion.get()) {
                    responsesReceived++;
                    System.out.println("Resposta positiva recebida para o documento " + version);
                    if (responsesReceived == totalClients) {
                        sendNextDocument();  // Enviar o próximo documento após todas as confirmações
                    }
                }
            } else if (received.startsWith(Constants.VERSION_CHECK_MESSAGE)) {
                System.out.println("Cliente solicitou versão atual.");
                String versionMessage = Constants.VERSION_CHECK_MESSAGE + "," + documentVersion.get();
                byte[] versionBuffer = versionMessage.getBytes();
                DatagramPacket versionPacket = new DatagramPacket(versionBuffer, versionBuffer.length, packet.getAddress(), packet.getPort());
                socket.send(versionPacket);
                System.out.println("Versão atual enviada: " + versionMessage);
            }
        } catch (IOException e) {
            System.err.println("Erro ao receber mensagem do cliente: " + e.getMessage());
        }
    }

    // Envia o próximo documento após as confirmações
    private void sendNextDocument() {
        if (responsesReceived == totalClients) {
            System.out.println("Todos os clientes confirmaram o recebimento do documento.");
            sendDocumentUpdate(); // Envia o próximo documento
        }
    }

    // Inicia o envio de heartbeats e atualizações de documentos
    private void startHeartbeatAndDocumentUpdates() {
        // Thread para enviar heartbeats
        new Thread(() -> {
            while (true) {
                sendHeartbeat();
                try {
                    Thread.sleep(2000); // Intervalo de 2 segundos entre heartbeats
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();

        new Thread(() -> {
            while (true) {
                sendDocumentUpdate();
                try {
                    Thread.sleep(10000); // Intervalo de 2 segundos entre heartbeats
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
                

    }

    @Override
    public void run() {
        System.out.println("Servidor líder em execução. Aguardando clientes...");

        // Escuta mensagens de clientes
        while (true) {

            receiveClientMessages();
        }
    }
}