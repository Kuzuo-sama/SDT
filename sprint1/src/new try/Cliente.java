import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import newtry.Item;

public class Cliente {

    

    public static void main(String[] args) {
        List<Item> itens = new ArrayList<>();
        itens.add(new Item("Doc1", "Conteudo1"));
        itens.add(new Item("Doc2", "Conteudo2"));
        itens.add(new Item("Doc3", "Conteudo3"));
        itens.add(new Item("Doc4", "Conteudo4"));
        itens.add(new Item("Doc5", "Conteudo5"));
        itens.add(new Item("Doc6", "Conteudo6"));
        itens.add(new Item("Doc7", "Conteudo7"));
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