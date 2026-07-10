import java.io.Serializable;
import java.util.List;

public class NetworkEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    // Core Event Headers: AUTH, JOIN_ROOM, CHAT, STREAM_CHUNK, GET_PLAYLIST, PLAYLIST_DATA, SYNC_PLAYBACK
    public String type;
    public String sender;
    public String roomId;
    public String payload;     // Used for general messages or selected track titles
    public byte[] binaryData;

    // Playlist & Media Sync Telemetry
    public List<String> playlistTracks;
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