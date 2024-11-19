import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;

public class Client extends Thread {
    private final MulticastSocket multicastSocket;
    private final DatagramSocket unicastSocket;
    private final InetAddress group;
    private InetSocketAddress socket;
    private int sendPort;
    private int receivePort;
    private final InetAddress leaderAddress;
    private final int leaderPort;
    private boolean joined = false;
    private String documentVersion;

    public Client(InetAddress group, int sendPort, int receivePort, InetAddress leaderAddress, int leaderPort) throws IOException {
    this.group = group;
    this.sendPort = sendPort;
    this.receivePort = receivePort;
    this.leaderAddress = leaderAddress;
    this.leaderPort = leaderPort;

    // Inicializa o MulticastSocket
    this.multicastSocket = new MulticastSocket(Constants.SEND_PORT);

    // Define o Time-to-Live
    multicastSocket.setTimeToLive(255);

    // Especifica a interface de rede (opcional, se necessário)
    NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());

    // Junta-se ao grupo multicast
    multicastSocket.joinGroup(new InetSocketAddress(group, receivePort), networkInterface);

    // Inicializa o UnicastSocket
    this.unicastSocket = new DatagramSocket(receivePort + 1);

    System.out.println("Client iniciado no grupo multicast: " + group.getHostAddress() + ":" + receivePort);
    sendJoin();
}


    public synchronized void sendJoin() {
        try {
            String joinMsg = Constants.JOIN_MESSAGE + "," + sendPort + "," + receivePort;
            byte[] buffer = joinMsg.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, leaderAddress, leaderPort);
            multicastSocket.send(packet);
            unicastSocket.send(packet);
            joined = true;
            notifyAll();
            System.out.println("JOIN enviado para o líder.");
        } catch (IOException e) {
            System.err.println("Erro ao enviar JOIN: " + e.getMessage());
        }
    }

    
    private void receiveMulticastMessage() {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    
        try {
            multicastSocket.setSoTimeout(1);
            
        } catch (IOException e) {
            System.err.println("Erro ao configurar tempo limite do socket: " + e.getMessage());
            return;
        }
    
        System.out.println("Aguardando mensagens multicast...");
        while (true) {
            try {
                multicastSocket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength()).trim();
                
                processMessage(received);
            } catch (java.net.SocketTimeoutException e) {
                // Timeout silencioso; nenhuma mensagem recebida
            } catch (IOException e) {
                System.err.println("Erro ao receber mensagem multicast: " + e.getMessage());
                break; // Sai do loop em caso de erro crítico
            }
        }
    }
    

    private void receiveUnicastMessage() {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    
        try {
            // Configura um tempo limite de 2 segundos para o socket
            unicastSocket.setSoTimeout(1);
        } catch (IOException e) {
            System.err.println("Erro ao configurar tempo limite do socket: " + e.getMessage());
            return;
        }
    
        System.out.println("Aguardando mensagens unicast...");
        while (true) {
            try {
                unicastSocket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength()).trim();
                System.out.println("Mensagem recebida via Unicast: " + received);
                processMessage(received);
            } catch (java.net.SocketTimeoutException e) {
               
            } catch (IOException e) {
                System.err.println("Erro ao receber mensagem unicast: " + e.getMessage());
                break; // Sai do loop em caso de erro crítico
            }
        }
    }
    

    private void processMessage(String received) {
        if ("ACK_JOIN".equals(received)) {
            System.out.println("ACK_JOIN recebido do líder. Conexão com o grupo confirmada.");
        } else if (received.startsWith(Constants.DOCUMENT_PREFIX)) {
            System.out.println("Documento recebido: " + received);
            documentVersion = received.split(",")[1];
            System.out.println(documentVersion);
            sendDocumentReceivedReply();
        } else if (received.equals(Constants.COMMIT_MESSAGE)) {
            System.out.println("Commit recebido. Nova versão do documento aplicada.");
        } else if (received.startsWith(Constants.VERSION_CHECK_MESSAGE)) {
            System.out.println("Versão recebida do líder: " + received);
            sendVersionReply();
        } else if (received.startsWith("NEW_PORTS")) {
            String[] newPorts = received.split(",");
            sendPort = Integer.parseInt(newPorts[1]);
            receivePort = Integer.parseInt(newPorts[2]);
            System.out.println("Novas portas recebidas do líder: " + sendPort + ", " + receivePort);
            sendJoin();
        }
    }
    
    

    public synchronized void sendDocumentReceivedReply() {
        try {
            String reply = "DOCUMENT_RECEIVED"+ "," + documentVersion;
            byte[] buffer = reply.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, leaderAddress, leaderPort);
            multicastSocket.send(packet);
            unicastSocket.send(packet);
            System.out.println("Resposta positiva enviada para o líder. Documento " + documentVersion + " recebido com sucesso.");
        } catch (IOException e) {
            System.err.println("Erro ao enviar resposta positiva para o líder: " + e.getMessage());
        }
    }

    public synchronized void sendVersionReply() {
        try {
            String versionReply = Constants.VERSION_CHECK_MESSAGE + "," + documentVersion;
            byte[] buffer = versionReply.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, leaderAddress, leaderPort);
            multicastSocket.send(packet);
            unicastSocket.send(packet);
            System.out.println("Versão enviada para o líder: " + documentVersion);
        } catch (IOException e) {
            System.err.println("Erro ao enviar versão: " + e.getMessage());
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

        Thread multicastReceiver = new Thread(() -> {
            while (true) {
                receiveMulticastMessage();
            }
        });

        Thread unicastReceiver = new Thread(() -> {
            while (true) {
                receiveUnicastMessage();
            }
        });

        multicastReceiver.start();
        unicastReceiver.start();

        try {
            multicastReceiver.join();
            unicastReceiver.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}