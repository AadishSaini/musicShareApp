import java.io.Serializable;

public class NetworkEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    public String type;
    public String sender;
    public String roomId;
    public String payload;
    public byte[] binaryData;

    public NetworkEvent(String type, String sender, String roomId, String payload) {
        this.type = type;
        this.sender = sender;
        this.roomId = roomId;
        this.payload = payload;
    }

    public NetworkEvent(String type, String sender, String roomId, byte[] binaryData) {
        this.type = type;
        this.sender = sender;
        this.roomId = roomId;
        this.binaryData = binaryData;
    }
}