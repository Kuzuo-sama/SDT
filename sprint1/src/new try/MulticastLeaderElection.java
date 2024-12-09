import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;
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
    
    private static List<Item> localItems = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> operationLog = Collections.synchronizedList(new ArrayList<>());

    private static MulticastSocket socket;

    public static void main(String[] args) {
        try {
            id = "Member-" + Instant.now().toEpochMilli();
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket = new MulticastSocket(PORT);
            socket.joinGroup(group);

            logMessage(id + " joined multicast group, starting discovery...");

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
            logMessage("mandei heartbeat");
            sendMessage(group, message);

            if (isLeader) {
                String logContent = readLogFile();
                sendMessage(group, "LOGFILE|" + id + "|" + logContent);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void syncDocuments(InetAddress group, int x) {
        if (isLeader) {
            if (x >= 0 && x < localItems.size()) {
                Item document = localItems.get(x);
                StringBuilder messageBuilder = new StringBuilder();
                
                messageBuilder.append("SYNC")
                            .append("|")
                            .append(id)
                            .append("|")
                            .append(docnum)
                            .append("|")
                            .append(document.getNome())
                            .append(";")
                            .append(document.getConteudo())
                            .append("\n");
                
                String message = messageBuilder.toString();
                
                try {
                    sendMessage(group, message);
                    logMessage("Sent: " + message);
                } catch (IOException e) {
                    logMessage("Error sending SYNC message: " + e.getMessage());
                }
            } else {
                logMessage("Invalid index: " + x + ". Please provide a value between 0 and " + (localItems.size() - 1) + ".");
            }
        }
    }

    private static void processmensagemcliente(String mensagem) {
        String semPrefixo = mensagem.replace("MENSAGEM: ", "").trim();
        String[] linhas = semPrefixo.split("\n");

        for (String linha : linhas) {
            String[] partes = linha.split(";");
            if (partes.length == 2) {
                String nome = partes[0].trim();
                String conteudo = partes[1].trim();

                Item item = new Item(nome, conteudo);
                for (Item i : localItems) {
                    if (i.getNome().equals(item.getNome())) {
                        return;
                    }
                }
                localItems.add(item);
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
                    logMessage("Received: " + message);
                    processSyncMessage(message);
                } else if (message.startsWith("YES")) {
                    processYesMessage(message);
                } else if (message.startsWith("LOGFILE")) {
                    processLogFileMessage(message);
                }

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

        if (partes.length == 4) {
            String id = partes[1];
            int docnum = Integer.parseInt(partes[2]);
            String[] documentParts = partes[3].split(";");
            if (documentParts.length == 2) {
                String nome = documentParts[0].trim();
                String conteudo = documentParts[1].trim();

                Item documento = new Item(nome, conteudo);

                if (localItems.size() == 0) {
                    localItems.add(documento);
                } else if (localItems.size() > 0) {
                    for (Item i : localItems) {
                        if (i.getNome().equals(documento.getNome())) {
                            logMessage("Documento já existe");
                            sendYesToLeader();
                            return;
                        }
                    }
                    localItems.add(documento);
                }

                logMessage("ID: " + id);
                logMessage("Docnum: " + docnum);
                logMessage("Nome: " + documento.getNome());
                logMessage("Conteúdo: " + documento.getConteudo());
            }
        }
    }

    private static void sendYesToLeader() {
        try {
            String message = "YES|" + id;

            if (currentLeader == null || currentleaderAddresses == null) {
                logMessage("No valid leader to send YES to.");
                return;
            }

            InetAddress leaderAddress = currentleaderAddresses;
            DatagramSocket unicastSocket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), leaderAddress, LEADER_UNICAST_PORT);
            unicastSocket.send(packet);
            unicastSocket.close();

            logMessage("Sent YES to leader at " + leaderAddress.getHostAddress());
        } catch (IOException e) {
            logMessage("Error sending YES to leader: " + e.getMessage());
        }
    }

    private static void processHeartbeat(String message) {
        String[] parts = message.split("\\|");
        String senderId = parts[1];
        String role = parts[2];

        if (!senderId.equals(id)) {
            members.put(senderId, Instant.now());
            
            if (role.equals("LEADER") && !hasLeader) {
                currentLeader = senderId;
                hasLeader = true;
                isLeader = false;
                logMessage("Discovered leader: " + senderId);
            }
        }
    }

    private static void processLeaderAnnouncement(String message) {
        String[] parts = message.split("\\|");
        String leaderId = parts[1];

        if (!leaderId.equals(id)) {
            currentLeader = leaderId;
            hasLeader = true;
            isLeader = false;
            logMessage("Leader is " + leaderId);
        }
    }

    private static void processYesMessage(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 2) {
            logMessage("Malformed YES message: " + message);
            return;
        }

        String senderId = parts[1];
        if (!senderId.equals(id)) {
            yesResponses.add(senderId);
            logMessage("Received YES from " + senderId);

            if (yesResponses.containsAll(members.keySet())) {
                yesResponses.clear();
                docnum++;
                logMessage("All members agreed. Incremented docnum to " + docnum);
            }
        }
    }

    private static void processLogFileMessage(String message) {
        String[] parts = message.split("\\|", 3);
        if (parts.length < 3) {
            logMessage("Malformed LOGFILE message: " + message);
            return;
        }

        String senderId = parts[1];
        String logContent = parts[2];

        if (!senderId.equals(id)) {
            try (PrintWriter out = new PrintWriter(new FileWriter("received_log.txt", true))) {
                out.println(logContent);
            } catch (IOException e) {
                logMessage("Error writing received log file: " + e.getMessage());
            }
        }
    }

    private static void monitorLeader(InetAddress group) {
        if (currentLeader != null && !currentLeader.equals(id)) {
            Instant lastSeen = members.get(currentLeader);
            long timeSinceLastSeen = lastSeen == null ? Long.MAX_VALUE : 
                Instant.now().toEpochMilli() - lastSeen.toEpochMilli();
    
            if (timeSinceLastSeen > TIMEOUT) {
                logMessage("Leader " + currentLeader + " is no longer active.");
                electNewLeader(group);
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
            logMessage(id + " recognizes new leader as " + currentLeader);
        }
    }

    private static void becomeLeader(InetAddress group) {
        if (hasLeader) {
            logMessage(id + " cannot become leader because there is already a leader.");
            return;
        }
        if (leaderSocket != null && !leaderSocket.isClosed()) {
            leaderSocket.close();
        }

        isLeader = true;
        hasLeader = true;
        currentLeader = id;
        logMessage(id + " is the new leader.");
    
        try {
            leaderSocket = new DatagramSocket(LEADER_UNICAST_PORT);
            logMessage("Leader unicast socket initialized on port " + LEADER_UNICAST_PORT);
            new Thread(() -> listenForUnicastMessages()).start();
        } catch (IOException e) {
            logMessage("Failed to initialize leader unicast socket: " + e.getMessage());
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
                    logMessage("Received: " + message);
                    processYesMessage(message);
                } else if (message.startsWith("MENSAGEM")) {
                    processmensagemcliente(message);
                }
            } catch (IOException e) {
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

private static void logMessage(String message) {
    System.out.println(message); // Print to terminal
    try (PrintWriter out = new PrintWriter(new FileWriter("log.txt", true))) {
        out.println(message); // Log to file
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private static String readLogFile() throws IOException {
    StringBuilder logContent = new StringBuilder();
    try (BufferedReader br = new BufferedReader(new FileReader("log.txt"))) {
        String line;
        while ((line = br.readLine()) != null) {
            logContent.append(line).append("\n");
        }
    }
    return logContent.toString();
}
}
