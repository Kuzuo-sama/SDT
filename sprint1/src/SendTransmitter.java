import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.List;
import java.util.ArrayList;

public class SendTransmitter extends Thread {
    private final MulticastSocket socket;
    private final InetAddress group;
    private final int port;
    private final Elemento elemento;
    private final List<String> listaMsgs;
    private int ackCount = 0;
    private final int quorum;
    private boolean awaitingAcks = false;
    private boolean committed = false;

    public SendTransmitter(InetAddress group, int port, Elemento elemento, int quorum) throws IOException {
        this.socket = new MulticastSocket();
        this.group = group;
        this.port = port;
        this.elemento = elemento;
        this.listaMsgs = new ArrayList<>();
        this.quorum = quorum;
        listaMsgs.add("Doc v2: Nova versão do documento...");
    }

    public void sendDocument() {
        try {
            if (!awaitingAcks) {
                String msg = String.join(",", listaMsgs);
                byte[] buffer = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);
                socket.send(packet);
                awaitingAcks = true;
                System.out.println("Documento enviado pelo líder: " + msg);
            }
        } catch (IOException e) {
            System.err.println("Erro ao enviar documento: " + e.getMessage());
        }
    }

    public void sendCommit() {
        try {
            String commitMsg = "COMMIT";
            byte[] buffer = commitMsg.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);
            socket.send(packet);
            committed = true;
            System.out.println("Commit enviado para todos os elementos.");
        } catch (IOException e) {
            System.err.println("Erro ao enviar commit: " + e.getMessage());
        }
    }

    public synchronized void receiveAck() {
        ackCount++;
        System.out.println("ACK recebido. Total de ACKs: " + ackCount);

        if (ackCount >= quorum && !committed) {
            sendCommit();
            awaitingAcks = false;
            ackCount = 0;
        }
    }

    @Override
    public void run() {
        while (true) {
            if (elemento.isLeader()) {
                sendDocument();
                System.out.println("Aguardando ACKs...");
            }

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                System.out.println("Pacote recebido de " + packet.getAddress().getHostAddress());
                String message = new String(packet.getData(), 0, packet.getLength());

                if ("ACK".equals(message)) {
                    receiveAck();
                } else if ("COMMIT".equals(message)) {
                    System.out.println("Commit recebido.");
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
