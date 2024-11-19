public class ArpMessage {
    private final String type; // "REQUEST" or "REPLY"
    private final String senderIp;
    private final String targetIp;

    public ArpMessage(String type, String senderIp, String targetIp) {
        this.type = type;
        this.senderIp = senderIp;
        this.targetIp = targetIp;
    }

    public String getType() {
        return type;
    }

    public String getSenderIp() {
        return senderIp;
    }

    public String getTargetIp() {
        return targetIp;
    }

    @Override
    public String toString() {
        return type + "," + senderIp + "," + targetIp;
    }

    public static ArpMessage fromString(String message) {
        String[] parts = message.split(",");
        return new ArpMessage(parts[0], parts[1], parts[2]);
    }
}