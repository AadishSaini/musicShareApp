import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MusicShareServer {
    public static final int CHAT_PORT = 8080;
    public static final int STREAM_PORT = 8081;
    public static final int FILE_PORT = 8082;

    private static final Map<String, Set<ClientHandler>> rooms = new ConcurrentHashMap<>();
    private static final Set<String> authenticatedUsers = ConcurrentHashMap.newKeySet();
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    // Central Playlist Asset Catalog
    private static final List<String> centralPlaylist = Collections.unmodifiableList(Arrays.asList(
            "audio1",
            "audio2",
            "audio3",
            "audio4"
    ));

    public static void main(String[] args) {
        System.out.println("[SERVER CORE] Launching Tri-Service Music Platform...");
        threadPool.execute(() -> startService(CHAT_PORT, "CHAT/AUTH SERVICE"));
        threadPool.execute(() -> startService(STREAM_PORT, "AUDIO STREAM SERVICE"));
        threadPool.execute(() -> startService(FILE_PORT, "IMAGE TRANSFER SERVICE"));
    }

    private static void startService(int port, String serviceName) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[INIT] " + serviceName + " live on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket, port));
            }
        } catch (IOException e) {
            System.err.println("[CRITICAL] " + serviceName + " crashed: " + e.getMessage());
        }
    }

    public static List<String> getCentralPlaylist() {
        return centralPlaylist;
    }

    public static void joinRoom(String roomId, ClientHandler client) {
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(client);
    }

    public static void leaveRooms(ClientHandler client) {
        rooms.forEach((roomId, clients) -> clients.remove(client));
    }

    public static void broadcastToRoom(String roomId, NetworkEvent event, ClientHandler excludeClient, int servicePort) {
        Set<ClientHandler> clients = rooms.get(roomId);
        if (clients != null) {
            for (ClientHandler client : clients) {
                if (client != excludeClient && client.getServicePort() == servicePort) {
                    client.sendEvent(event);
                }
            }
        }
    }

    public static boolean registerUser(String username) { return authenticatedUsers.add(username); }
    public static void unregisterUser(String username) { if (username != null) authenticatedUsers.remove(username); }
}