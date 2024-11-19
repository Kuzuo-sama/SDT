import java.util.Arrays;
import java.util.List;

public class Constants {
    public static final String MULTICAST_GROUP = "230.0.0.1";
    public static final int SEND_PORT = 4446;
    public static final int RECEIVE_PORT = 4447;
    public static final int QUORUM = 2;
    public static final String HEARTBEAT_MESSAGE = "HEARTBEAT";
    public static final String JOIN_MESSAGE = "JOIN";
    public static final String REPLY_MESSAGE = "REPLY";
    public static final List<String> DOCUMENT_VERSIONS = Arrays.asList(
    "Doc 1",
    "Doc 2",
    "Doc 3",
    "Doc 4"
    );
    public static final String DOCUMENT_PREFIX = "DOCUMENT";

    public static final String COMMIT_MESSAGE = "COMMIT";
    public static final String VERSION_CHECK_MESSAGE = "VERSION_CHECK";
    
}