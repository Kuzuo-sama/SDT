import java.io.IOException;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class Cliente {

    public static class Item implements Serializable {
        private String nome;
        private String conteudo;

        public Item(String nome, String conteudo) {
            this.nome = nome;
            this.conteudo = conteudo;
        }

        public String getNome() {
            return nome;
        }

        public String getConteudo() {
            return conteudo;
        }
    }

    public static void main(String[] args) {
        List<Item> itens = new ArrayList<>();
        itens.add(new Item("Item1", "Conteudo1"));
        itens.add(new Item("Item2", "Conteudo2"));
        while(true){
        try {
            for (Item item : itens) {
                DatagramSocket socket = new DatagramSocket();
                InetAddress address = InetAddress.getByName("localhost");
                int port = 5001;
        
                // Construir a mensagem no formato desejado
                StringBuilder messageBuilder = new StringBuilder();
                
                    messageBuilder.append("MENSAGEM: ")
                                .append(item.getNome())
                                .append(";")
                                .append(item.getConteudo())
                                .append("\n");
                
                String message = messageBuilder.toString();
                
        
                // Enviar a mensagem
                byte[] buf = message.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
                socket.send(packet);
                boolean leaderFound = false;
        
                while (!leaderFound) {
                    // Simulate checking for a leader
                    if (address.isReachable(1000)) {
                        leaderFound = true;
                        System.out.println("Leader found and message sent to port 5001");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        }       
    }
}