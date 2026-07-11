import java.io.Serializable;
import java.util.List;

public class NetworkEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    public String type;        // AUTH, JOIN_ROOM, CHAT, STREAM_CHUNK, GET_PLAYLIST, PLAYLIST_DATA
    public String sender;
    public String roomId;
    public String payload;
    public byte[] binaryData;  // Holds raw microphone OR raw song audio bytes
    public List<String> playlistTracks;

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