import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;

public class MusicShareServer {
    public static final int CHAT_PORT = 8080;
    public static final int STREAM_PORT = 8081;
    public static final int FILE_PORT = 8082;

    // Room registry mapping Room IDs to Room Objects
    private static final Map<String, Room> activeRooms = new ConcurrentHashMap<>();
    private static final Set<String> authenticatedUsers = ConcurrentHashMap.newKeySet();
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.out.println("[SERVER CORE] Launching Multi-Room Streaming Engine...");
        threadPool.execute(() -> startService(CHAT_PORT, "CHAT/AUTH SERVICE"));
        threadPool.execute(() -> startService(STREAM_PORT, "AUDIO STREAM SERVICE"));
        threadPool.execute(() -> startService(FILE_PORT, "IMAGE TRANSFER SERVICE"));
    }

    private static void startService(int port, String serviceName) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // Active visual trace so you know the ports are up!
            System.out.println(" --> [OK] " + serviceName + " successfully initialized on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Trace every incoming socket connection attempt in real-time
                System.out.println(" --> [CONNECTION] Inbound channel established on port " + port + " from " + clientSocket.getRemoteSocketAddress());
                threadPool.execute(new ClientHandler(clientSocket, port));
            }
        } catch (IOException e) {
            System.err.println("[CRITICAL] " + serviceName + " crashed on port " + port + ": " + e.getMessage());
        }
    }

    public static void joinRoom(String roomId, ClientHandler client) {
        activeRooms.computeIfAbsent(roomId, Room::new).addClient(client);
    }

    public static void leaveRooms(ClientHandler client) {
        activeRooms.values().forEach(room -> room.removeClient(client));
    }

    public static void broadcastToRoom(String roomId, NetworkEvent event, ClientHandler excludeClient, int servicePort) {
        Room room = activeRooms.get(roomId);
        if (room != null) {
            room.broadcast(event, excludeClient, servicePort);
        }
    }

    public static void playTrackInRoom(String roomId, int trackIndex) {
        Room room = activeRooms.get(roomId);
        if (room != null) {
            threadPool.execute(() -> room.streamAudioTrack(trackIndex));
        }
    }

    public static List<String> getCentralPlaylist() {
        List<String> tracks = new ArrayList<>();
        File folder = new File("music");
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            int index = 1;
            for (File file : listOfFiles) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".wav") || name.endsWith(".mp3")) {
                    tracks.add(index + ". " + file.getName());
                    index++;
                }
            }
        }
        if (tracks.isEmpty()) tracks.add("[No tracks found in ../music]");
        return tracks;
    }

    public static boolean registerUser(String username) { return authenticatedUsers.add(username); }
    public static void unregisterUser(String username) { if (username != null) authenticatedUsers.remove(username); }

    // --- NESTED ROOM CLASS MANAGER ---
    private static class Room {
        private final String roomId;
        private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
        private volatile boolean isPlaying = false;

        public Room(String roomId) { this.roomId = roomId; }

        public synchronized void addClient(ClientHandler c) { clients.add(c); }
        public synchronized void removeClient(ClientHandler c) { clients.remove(c); }

        public void broadcast(NetworkEvent event, ClientHandler exclude, int port) {
            for (ClientHandler client : clients) {
                if (client != exclude && client.getServicePort() == port) {
                    client.sendEvent(event);
                }
            }
        }

        // Isolated multi-room audio stream loop
        public void streamAudioTrack(int index) {
            List<String> rawTracks = getCentralPlaylist();
            if (index < 0 || index >= rawTracks.size()) return;

            // Stop any currently playing song in this specific room
            this.isPlaying = false;
            try { Thread.sleep(100); } catch (Exception ignored) {}

            String rawName = rawTracks.get(index).substring(3);
            File audioFile = new File("music", rawName);
            this.isPlaying = true;

            System.out.println("[ROOM STREAM] Room [" + roomId + "] starting broadcast of: " + rawName);

            try (AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile)) {
                AudioFormat targetFormat = new AudioFormat(44100, 16, 1, true, true);
                try (AudioInputStream decodedAis = AudioSystem.getAudioInputStream(targetFormat, ais)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    while (this.isPlaying && (bytesRead = decodedAis.read(buffer, 0, buffer.length)) != -1) {
                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunk, 0, bytesRead);

                        NetworkEvent audioEvent = new NetworkEvent("STREAM_CHUNK", "SERVER", roomId, chunk);
                        broadcast(audioEvent, null, STREAM_PORT);

                        Thread.sleep(23); // Standard throttle pace
                    }
                }
            } catch (Exception e) {
                System.err.println("[ROOM STREAM ERROR] Room " + roomId + " failed: " + e.getMessage());
            }
            System.out.println("[ROOM STREAM] Room [" + roomId + "] finished streaming.");
        }
    }
}