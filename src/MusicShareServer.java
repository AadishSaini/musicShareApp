import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MusicShareServer {
    private static final int PORT = 8080;
    private static final Map<String, Set<ClientHandler>> rooms = new ConcurrentHashMap<>();
    private static final Set<String> authenticatedUsers = ConcurrentHashMap.newKeySet();
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.out.println("[SERVER] MusicShare Backend starting on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[CONNECTION] New client connected from " + clientSocket.getRemoteSocketAddress());
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("[SERVER ERROR] Exception: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    public static void joinRoom(String roomId, ClientHandler client) {
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(client);
        System.out.println("[ROOM] " + client.getUsername() + " joined room: " + roomId);
    }

    public static void leaveRooms(ClientHandler client) {
        rooms.forEach((roomId, clients) -> {
            if (clients.remove(client)) {
                System.out.println("[ROOM] " + client.getUsername() + " left room: " + roomId);
            }
        });
    }

    public static void broadcastToRoom(String roomId, NetworkEvent event, ClientHandler excludeClient) {
        Set<ClientHandler> clients = rooms.get(roomId);
        if (clients != null) {
            for (ClientHandler client : clients) {
                if (client != excludeClient) {
                    client.sendEvent(event);
                }
            }
        }
    }

    public static boolean registerUser(String username) {
        return authenticatedUsers.add(username);
    }

    public static void unregisterUser(String username) {
        if (username != null) authenticatedUsers.remove(username);
    }
}