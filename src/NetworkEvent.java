import java.io.Serializable;

public class NetworkEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    public String type;        // AUTH, JOIN_ROOM, CHAT, STREAM_CHUNK, MEDIA_TRANSFER, SYNC_PLAYBACK
    public String sender;      // Username
    public String roomId;      // Target Room
    public String payload;     // Message text, song title, or filename
    public byte[] binaryData;  // For audio raw data or image transfer bytes

    // Resume Sync Engine Properties
    public long trackProgressMs;
    public boolean isPlaying;

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