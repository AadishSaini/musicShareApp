import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private String currentRoomId;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUsername() { return this.username; }

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
                        handleRoomJoin(event);
                        break;
                    case "CHAT":
                    case "STREAM_CHUNK":
                    case "MEDIA_TRANSFER":
                        handleRoomBroadcast(event);
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("[DISCONNECT] Connection lost with user: " + username);
        } finally {
            cleanup();
        }
    }

    private void handleAuthentication(NetworkEvent event) throws IOException {
        if (MusicShareServer.registerUser(event.sender)) {
            this.username = event.sender;
            sendEvent(new NetworkEvent("AUTH_SUCCESS", "SERVER", null, "Welcome!"));
            System.out.println("[AUTH] Authenticated user: " + username);
        } else {
            sendEvent(new NetworkEvent("AUTH_FAIL", "SERVER", null, "Username taken."));
        }
    }

    private void handleRoomJoin(NetworkEvent event) {
        if (this.username == null) return;
        MusicShareServer.leaveRooms(this);
        this.currentRoomId = event.roomId;
        MusicShareServer.joinRoom(event.roomId, this);
        MusicShareServer.broadcastToRoom(currentRoomId, new NetworkEvent("SYSTEM", "SERVER", currentRoomId, username + " joined the room."), this);
    }

    private void handleRoomBroadcast(NetworkEvent event) {
        if (currentRoomId != null) {
            MusicShareServer.broadcastToRoom(currentRoomId, event, this);
        }
    }

    public void sendEvent(NetworkEvent event) {
        try {
            out.writeObject(event);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error dispatching packet to " + username);
        }
    }

    private void cleanup() {
        MusicShareServer.unregisterUser(username);
        MusicShareServer.leaveRooms(this);
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}