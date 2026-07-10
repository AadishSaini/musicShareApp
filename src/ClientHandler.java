import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private int servicePort;
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
                NetworkEvent event = (NetworkEvent) in.readObject();
                if (event == null) break;

                switch (event.type) {
                    case "AUTH":
                        handleAuthentication(event);
                        break;
                    case "JOIN_ROOM":
                        this.username = event.sender;
                        this.currentRoomId = event.roomId;
                        MusicShareServer.joinRoom(event.roomId, this);
                        break;
                    case "GET_PLAYLIST":
                        // Pull the global playlist catalog and return it exclusively to this client
                        NetworkEvent playlistResponse = new NetworkEvent("PLAYLIST_DATA", "SERVER", currentRoomId, "GLOBAL_CATALOG");
                        playlistResponse.playlistTracks = MusicShareServer.getCentralPlaylist();
                        sendEvent(playlistResponse);
                        break;
                    case "CHAT":
                    case "STREAM_CHUNK":
                    case "MEDIA_TRANSFER":
                    case "SYNC_PLAYBACK":
                        if (currentRoomId != null) {
                            MusicShareServer.broadcastToRoom(currentRoomId, event, this, this.servicePort);
                        }
                        break;
                }
            }
        } catch (Exception e) {
            // Clean up disconnected states quietly
        } finally {
            cleanup();
        }
    }

    private void handleAuthentication(NetworkEvent event) throws IOException {
        if (MusicShareServer.registerUser(event.sender)) {
            this.username = event.sender;
            sendEvent(new NetworkEvent("AUTH_SUCCESS", "SERVER", null, "Authenticated."));
        } else {
            sendEvent(new NetworkEvent("AUTH_FAIL", "SERVER", null, "Username taken."));
        }
    }

    public void sendEvent(NetworkEvent event) {
        try {
            out.writeObject(event);
            out.flush();
        } catch (IOException ignored) {}
    }

    private void cleanup() {
        MusicShareServer.unregisterUser(username);
        MusicShareServer.leaveRooms(this);
        try { socket.close(); } catch (IOException ignored) {}
    }
}