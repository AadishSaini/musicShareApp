import java.io.*;
import java.net.*;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final int servicePort;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private String currentRoomId;

    public ClientHandler(Socket socket, int servicePort) {
        this.socket = socket;
        this.servicePort = servicePort;
    }

    public String getUsername() { return this.username; }
    public int getServicePort() { return this.servicePort; }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                NetworkEvent event;
                try {
                    // Transport-level failure (socket closed, corrupt stream) -> disconnect this client.
                    event = (NetworkEvent) in.readObject();
                } catch (EOFException | SocketException eof) {
                    break;
                }
                if (event == null) break;

                try {
                    // A problem handling ONE command should never take down the whole connection,
                    // so command-processing errors are caught here and logged instead of propagating
                    // up to the transport-level catch below.
                    handleEvent(event);
                } catch (Exception commandError) {
                    System.err.printf("[COMMAND ERROR] From '%s': %s (event type=%s)\n",
                            username, commandError.getMessage(), event.type);
                }
            }
        } catch (Exception e) {
            // Drop connection quietly on clean window close / unreadable stream.
        } finally {
            cleanup();
        }
    }

    private void handleEvent(NetworkEvent event) {
        switch (event.type) {
            case "AUTH":
                if (MusicShareServer.registerUser(event.sender)) {
                    this.username = event.sender;
                    sendEvent(new NetworkEvent("AUTH_SUCCESS", "SERVER", null, "Authenticated."));
                } else {
                    sendEvent(new NetworkEvent("AUTH_FAIL", "SERVER", null, "Username taken."));
                }
                break;

            case "JOIN_ROOM":
                this.username = event.sender;
                this.currentRoomId = event.roomId;
                MusicShareServer.joinRoom(event.roomId, this);
                break;

            case "GET_PLAYLIST":
                System.out.printf("[COMMAND] User '%s' requested the playlist catalog from Room [%s]\n", username, currentRoomId);
                NetworkEvent playlistResponse = new NetworkEvent("PLAYLIST_DATA", "SERVER", currentRoomId, "GLOBAL_CATALOG");
                playlistResponse.playlistTracks = MusicShareServer.getCentralPlaylist();
                sendEvent(playlistResponse);
                break;

            case "SYNC_PLAYBACK":
                handleSyncPlayback(event);
                break;

            case "CHAT":
                System.out.printf("[TEXT CHAT] [%s] %s: %s\n", currentRoomId, username, event.payload);
                MusicShareServer.broadcastToRoom(currentRoomId, event, this, this.servicePort);
                break;

            case "STREAM_CHUNK":
                // Keep voice/mic data moving quietly without printing to prevent console flooding
                MusicShareServer.broadcastToRoom(currentRoomId, event, this, this.servicePort);
                break;

            default:
                MusicShareServer.broadcastToRoom(currentRoomId, event, this, this.servicePort);
                break;
        }
    }

    private void handleSyncPlayback(NetworkEvent event) {
        if (event.payload == null || !event.payload.startsWith("PLAY_TRACK_INDEX:") || currentRoomId == null) {
            return;
        }

        String rawIndex = event.payload.split(":")[1];
        int trackIndex;
        try {
            trackIndex = Integer.parseInt(rawIndex.trim()) - 1;
        } catch (NumberFormatException nfe) {
            System.out.printf("[COMMAND] User '%s' sent a non-numeric track index: '%s'\n", username, rawIndex);
            sendEvent(new NetworkEvent("CHAT", "SERVER", currentRoomId, "That's not a valid track number. Use /playlist to see indexes."));
            return;
        }

        // Validate BEFORE telling the room to start playing, so a bad index can never leave the
        // room's playback state stuck "playing" a track that doesn't exist.
        List<String> tracks = MusicShareServer.getCentralPlaylist();
        if (trackIndex < 0 || trackIndex >= tracks.size()) {
            System.out.printf("[COMMAND] User '%s' requested out-of-range track index %d\n", username, trackIndex + 1);
            sendEvent(new NetworkEvent("CHAT", "SERVER", currentRoomId,
                    "Invalid track index. There are only " + tracks.size() + " tracks. Use /playlist to see them."));
            return;
        }

        System.out.printf("[COMMAND] User '%s' triggered track index %d inside Room [%s]\n", username, trackIndex + 1, currentRoomId);
        MusicShareServer.playTrackInRoom(currentRoomId, trackIndex);
    }

    public synchronized void sendEvent(NetworkEvent event) {
        try {
            out.writeObject(event);
            out.flush();
            out.reset(); // Strict serialization clearing to eliminate line latency
        } catch (IOException ignored) {}
    }

    private void cleanup() {
        MusicShareServer.unregisterUser(username);
        MusicShareServer.leaveRooms(this);
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}