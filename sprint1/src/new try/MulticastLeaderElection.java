import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import newtry.Item;

public class MulticastLeaderElection {
    private static final String MULTICAST_GROUP = "224.0.0.1";
    private static final int PORT = 5000;
    private static final int HEARTBEAT_INTERVAL = 3000;
    private static final int TIMEOUT = 5000;
    private static int LEADER_UNICAST_PORT = 5001;
    private static DatagramSocket leaderSocket;
    private static InetAddress currentleaderAddresses;

    
    private static final AtomicInteger yesCount = new AtomicInteger(0);
    private static final Set<String> yesResponses = Collections.synchronizedSet(new HashSet<>());
    private static int docnum = 0;
    private static String id;
    private static boolean isLeader = false;
    private static boolean hasLeader = false;
    private static String currentLeader = null;

    private static final Map<String, Instant> members = new ConcurrentHashMap<>();
    private static List<Item> itens = new ArrayList<>();
    private static List<Item> localItems = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> operationLog = Collections.synchronizedList(new ArrayList<>());

    private static MulticastSocket socket;

    public static void main(String[] args) {
        try {
            id = "Member-" + Instant.now().toEpochMilli();
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket = new MulticastSocket(PORT);
            socket.joinGroup(group);

            System.out.println(id + " joined multicast group, starting discovery...");

            AtomicBoolean discoveryComplete = new AtomicBoolean(false);

            
            new Thread(() -> listenForMessages(group, discoveryComplete)).start();

            while (!discoveryComplete.get()) {
                Thread.sleep(100);
            }

            if (!hasLeader) {
                becomeLeader(group);
            }

            new Thread(() -> {
                try {
                    while (true) {
                        if (isLeader) {
                            sendHeartbeat(group);
                        }
                        Thread.sleep(HEARTBEAT_INTERVAL);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

            new Thread(() -> {
                try {
                    while (true) {
                        if (isLeader) {
                            syncDocuments(group, docnum);
                        }
                        Thread.sleep(HEARTBEAT_INTERVAL);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

            new Thread(() -> {
                try {
                    while (true) {
                        monitorLeader(group);
                        removeInactiveNodes();
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void sendHeartbeat(InetAddress group) {
        try {
            String message = "HEARTBEAT|" + id + "|" + (isLeader ? "LEADER" : "MEMBER");
            System.out.println("mandei heartbeat");
            sendMessage(group, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void syncDocuments(InetAddress group, int x) {
        if (isLeader) {
            // Lista fixa com os nomes dos documentos
            
    
            if (x >= 0 && x < itens.size()) {
                Item document = itens.get(x);
                StringBuilder messageBuilder = new StringBuilder();
                
                    messageBuilder.append("SYNC")
                                .append("|")
                                .append(id)
                                .append("|")
                                .append(document.getNome())
                                .append(";")
                                .append(document.getConteudo())
                                .append("\n");
                
                String message = messageBuilder.toString();
                
    
                try {
                    sendMessage(group, message);
                    System.out.println("Sent: " + message);
    
                    
                } catch (IOException e) {
                    System.err.println("Error sending SYNC message: " + e.getMessage());
                }
            } else {
                System.err.println("Invalid index: " + x + ". Please provide a value between 0 and " + (itens.size() - 1) + ".");
            }
        }
    }
    

    private static void processmensagemcliente(String mensagem){

        String semPrefixo = mensagem.replace("MENSAGEM: ", "").trim();

        // Quebrar em linhas (se houver várias mensagens)
        String[] linhas = semPrefixo.split("\n");

        

        for (String linha : linhas) {
            // Quebrar no delimitador ";"
            String[] partes = linha.split(";");
            if (partes.length == 2) {
                String nome = partes[0].trim();
                String conteudo = partes[1].trim();

                // Criar e adicionar o item à lista
                Item item = new Item(nome, conteudo);
                for (Item i : itens) {
                    if (i.getNome().equals(item.getNome())) {
                        System.out.println("Documento já existe");
                        return;
                    }
                }
                itens.add(item);
            }
        }
    }
    
    

    private static void listenForMessages(InetAddress group, AtomicBoolean discoveryComplete) {
        byte[] buffer = new byte[1024];
        long discoveryStart = System.currentTimeMillis();

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.setSoTimeout(100);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());

                if (message.startsWith("HEARTBEAT")) {
                    currentleaderAddresses = packet.getAddress();
                    processHeartbeat(message);
                } else if (message.startsWith("LEADER")) {
                    currentleaderAddresses = packet.getAddress();
                    processLeaderAnnouncement(message);
                } else if (message.startsWith("SYNC")) {
                    System.out.println("Received: " + message);
                    processSyncMessage(message);
                } else if (message.startsWith("YES")) {
                    processYesMessage(message);
                }/*else if (message.startsWith("STATE")) {
                    processStateMessage(message);
                }*/

                if (System.currentTimeMillis() - discoveryStart > TIMEOUT || !members.isEmpty() || hasLeader) {
                    discoveryComplete.set(true);
                }
                
            } catch (IOException e) {
                if (System.currentTimeMillis() - discoveryStart > TIMEOUT) {
                    discoveryComplete.set(true);
                }
            }
        }
    }

    private static void processSyncMessage(String message) {
        String[] partes = message.split("\\|");

        if (partes.length == 3) {
            String id = partes[1]; // Extrai o ID

            // Quebrar a última parte no delimitador ";" para obter nome e conteúdo
            String[] documentParts = partes[2].split(";");
            if (documentParts.length == 2) {
                String nome = documentParts[0].trim();
                String conteudo = documentParts[1].trim();

                // Criar um objeto Document ou qualquer estrutura necessária
                Item documento = new Item(nome, conteudo);

                // Adicionar o documento à lista local
                if(localItems.size() == 0){
                    localItems.add(documento);
                }else if(localItems.size() > 0){
                    for (Item i : localItems) {
                        if (i.getNome().equals(documento.getNome())) {
                            System.out.println("Documento já existe");
                            sendYesToLeader();
                            return;
                        }
                    }
                    localItems.add(documento);
                }

                // Exemplo de saída ou processamento
                System.out.println("ID: " + id);
                System.out.println("Nome: " + documento.getNome());
                System.out.println("Conteúdo: " + documento.getConteudo());
            }
        }

        
    }
    
    private static void sendYesToLeader() {
        try {
            String message = "YES|" + id;
    
            if (currentLeader == null || currentleaderAddresses == null) {
                System.err.println("No valid leader to send YES to.");
                return;
            }
    
            InetAddress leaderAddress = currentleaderAddresses;  // Obter o endereço IP do líder
            DatagramSocket unicastSocket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), leaderAddress, LEADER_UNICAST_PORT);
            unicastSocket.send(packet);
            unicastSocket.close();
    
            System.out.println("Sent YES to leader at " + leaderAddress.getHostAddress());
        } catch (IOException e) {
            System.err.println("Error sending YES to leader: " + e.getMessage());
        }
    }
    
    
    
    

    private static void processHeartbeat(String message) {
        String[] parts = message.split("\\|");
        String senderId = parts[1];
        String role = parts[2]; // Role added to heartbeat message

        if (!senderId.equals(id)) {
            members.put(senderId, Instant.now());
            
            // If we haven't found a leader yet and we receive a leader heartbeat, set it
            if (role.equals("LEADER") && !hasLeader) {
                currentLeader = senderId;
                hasLeader = true;
                isLeader = false; // This node is not the leader
                System.out.println("Discovered leader: " + senderId);
            }
        }
    }

    private static void processLeaderAnnouncement(String message) {
        String[] parts = message.split("\\|");
        String leaderId = parts[1];

        if (!leaderId.equals(id)) {
            
            currentLeader = leaderId;
            hasLeader = true;
            isLeader = false; // This node is not the leader
            System.out.println("Leader is " + leaderId);
        }
    }


    private static void processYesMessage(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 2) {
            System.err.println("Malformed YES message: " + message);
            return;
        }
    
        String senderId = parts[1];
        if (!senderId.equals(id)) {
            yesResponses.add(senderId);
            System.out.println("Received YES from " + senderId);
    
            // Check if all members have responded
            if (yesResponses.containsAll(members.keySet())) {
                yesResponses.clear();
                docnum++;
                System.out.println("All members agreed. Incremented docnum to " + docnum);
            }
        }
    }
    

    private static void monitorLeader(InetAddress group) {
        if (currentLeader != null && !currentLeader.equals(id)) {
            Instant lastSeen = members.get(currentLeader);
            long timeSinceLastSeen = lastSeen == null ? Long.MAX_VALUE : 
                Instant.now().toEpochMilli() - lastSeen.toEpochMilli();
            
            // Adiciona mensagens de depuração
            //System.out.println("Current leader: " + currentLeader);
            //System.out.println("Last seen: " + (lastSeen != null ? lastSeen.toString() : "never"));
            //System.out.println("Time since last seen: " + timeSinceLastSeen + "ms");
            //System.out.println("Timeout: " + TIMEOUT + "ms");
    
            // Verifica se o líder está inativo com base no tempo
            if (timeSinceLastSeen > TIMEOUT) {
                System.out.println("Leader " + currentLeader + " is no longer active.");
                electNewLeader(group); // Inicia eleição de um novo líder
            }
        }
    }
    

    private static void electNewLeader(InetAddress group) {
        members.entrySet().removeIf(entry -> 
            Instant.now().toEpochMilli() - entry.getValue().toEpochMilli() > TIMEOUT);

        hasLeader = false;

        currentLeader = members.entrySet()
                .stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(id);

        if (currentLeader.equals(id)) {
            
            becomeLeader(group);
        } else {
            hasLeader = true;
            isLeader = false;
            System.out.println(id + " recognizes new leader as " + currentLeader);
        }
    }

    private static void becomeLeader(InetAddress group) {
        if (hasLeader) {
            System.out.println(id + " cannot become leader because there is already a leader.");
            return;
        }
        if (leaderSocket != null && !leaderSocket.isClosed()) {
            leaderSocket.close();
        }

        
        
        isLeader = true;
        hasLeader = true;
        currentLeader = id;
        System.out.println(id + " is the new leader.");
    
        try {
            leaderSocket = new DatagramSocket(LEADER_UNICAST_PORT);

            System.out.println("Leader unicast socket initialized on port " + LEADER_UNICAST_PORT);
            
            new Thread(() -> listenForUnicastMessages()).start();

        } catch (IOException e) {
            System.err.println("Failed to initialize leader unicast socket: " + e.getMessage());
        }
    
        sendLeaderAnnouncement(group);
    }
    

    private static void listenForUnicastMessages() {
        byte[] buffer = new byte[1024];
        while (isLeader) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                leaderSocket.setSoTimeout(100);
                leaderSocket.receive(packet);
    
                String message = new String(packet.getData(), 0, packet.getLength());
                if (message.startsWith("YES")) {
                    System.out.println("Received: " + message);
                    processYesMessage(message); // Processar mensagens YES recebidas
                }else if(message.startsWith("MENSAGEM")){
                    System.out.println("Received: " + message);
                    processmensagemcliente(message);
                    
                }
            } catch (IOException e) {
                // Timeout é esperado, sem necessidade de erro aqui
            }
        }
    }
    
    



    private static void sendLeaderAnnouncement(InetAddress group) {
        try {
            String message = "LEADER|" + id;
            sendMessage(group, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void removeInactiveNodes() {
        members.entrySet().removeIf(entry -> 
            Instant.now().toEpochMilli() - entry.getValue().toEpochMilli() > TIMEOUT);
    }

    private static void sendMessage(InetAddress group, String message) throws IOException {
        DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), group, PORT);
        socket.send(packet);
    }
}



