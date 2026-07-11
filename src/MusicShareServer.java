import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;

public class MusicShareServer {
    public static final int CHAT_PORT = 8080;
    public static final int STREAM_PORT = 8081;
    public static final int FILE_PORT = 8082;

    private static final Map<String, Room> activeRooms = new ConcurrentHashMap<>();
    private static final Set<String> authenticatedUsers = ConcurrentHashMap.newKeySet();
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println("[SERVER CORE] Launching Multi-Room Telemetry Streaming Engine...");
        System.out.println("====================================================================");

        threadPool.execute(() -> startService(CHAT_PORT, "CHAT/AUTH SERVICE"));
        threadPool.execute(() -> startService(STREAM_PORT, "AUDIO STREAM SERVICE"));
        threadPool.execute(() -> startService(FILE_PORT, "IMAGE TRANSFER SERVICE"));
    }

    private static void startService(int port, String serviceName) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println(" --> [OK] " + serviceName + " initialized on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket, port));
            }
        } catch (IOException e) {
            System.err.println("[CRITICAL ERROR] " + serviceName + " crashed: " + e.getMessage());
        }
    }

    public static void joinRoom(String roomId, ClientHandler client) {
        System.out.printf("[ROOM ENGINE] User '%s' requested join to Room [%s] on Port %d\n",
                client.getUsername(), roomId, client.getServicePort());
        activeRooms.computeIfAbsent(roomId, Room::new).addClient(client);
    }

    public static void leaveRooms(ClientHandler client) {
        if (client.getUsername() != null) {
            System.out.printf("[ROOM ENGINE] Cleaning up links for User '%s' on Port %d\n",
                    client.getUsername(), client.getServicePort());
        }
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
            threadPool.execute(() -> room.startNewTrack(trackIndex));
        } else {
            System.err.printf("[PLAYBACK ERROR] Cannot play track. Room [%s] does not exist.\n", roomId);
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
        return tracks;
    }

    public static boolean registerUser(String username) {
        boolean success = authenticatedUsers.add(username);
        System.out.printf("[AUTH REGISTRY] Auth attempt for '%s' -> %s\n", username, success ? "SUCCESS" : "DENIED (Duplicate)");
        return success;
    }

    public static void unregisterUser(String username) {
        if (username != null && authenticatedUsers.remove(username)) {
            System.out.printf("[AUTH REGISTRY] User '%s' cleared from system memory.\n", username);
        }
    }

    // --- STATEFUL ROOM MANAGER WITH TELEMETRY ---
    private static class Room {
        private final String roomId;
        private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

        // ALL playback-related state (session id, playing flag, track index, byte offset,
        // and the chunk log) is guarded by ONE lock: streamLock. Client set membership is ALSO
        // guarded by streamLock whenever it interacts with playback (join-time catch-up), so that
        // "snapshot the chunks sent so far" and "start receiving future chunks" happen as a single
        // atomic step. That's what prevents a late joiner from getting chunks out of order.
        private final Object streamLock = new Object();

        private volatile long currentSessionId = 0;
        private volatile boolean isPlaying = false;
        private volatile int currentTrackIndex = -1;
        private volatile long currentByteOffset = 0;

        // Every chunk broadcast for the CURRENT track/session, in order. Used to catch up
        // late joiners without re-decoding the file and without racing the live broadcast.
        // Reset whenever a new track starts. Bounded by track length (a few MB for a typical song).
        private final List<byte[]> sessionChunkLog = new ArrayList<>();

        public Room(String roomId) { this.roomId = roomId; }

        public void addClient(ClientHandler c) {
            List<byte[]> catchUpSnapshot = null;

            synchronized (streamLock) {
                clients.add(c);
                boolean needsCatchUp = isPlaying && c.getServicePort() == STREAM_PORT
                        && currentTrackIndex != -1 && !sessionChunkLog.isEmpty();
                if (needsCatchUp) {
                    // Copy while still holding the lock: this is the atomic "snapshot + subscribe"
                    // step. Anything broadcast by streamAudioTrack AFTER this point will see this
                    // client already in `clients` and will be sent live, in order, after the
                    // snapshot below. Nothing can be duplicated or skipped.
                    catchUpSnapshot = new ArrayList<>(sessionChunkLog);
                }

                System.out.printf("  ↳ [ROOM STATE] [%s] Client Count: %d | Pipe adding: Port %d\n",
                        roomId, clients.size(), c.getServicePort());

                if (catchUpSnapshot != null) {
                    System.out.printf("  ↳ [SYNC] Sending %d buffered chunk(s) to late joiner in Room [%s] before resuming live stream\n",
                            catchUpSnapshot.size(), roomId);
                    // Sent WHILE STILL HOLDING streamLock. This briefly blocks the live broadcast
                    // loop (which also needs streamLock per chunk) so no live chunk can reach ANY
                    // client in the room until this catch-up burst is fully delivered. That's the
                    // guarantee that keeps the wire order correct: history first, then live.
                    for (byte[] chunk : catchUpSnapshot) {
                        c.sendEvent(new NetworkEvent("STREAM_CHUNK", "SERVER", roomId, chunk));
                    }
                    System.out.printf("[SYNC COMPLETED] Late joiner aligned with Room [%s] timeline stream.\n", roomId);
                }
            }
        }

        public void removeClient(ClientHandler c) {
            if (clients.remove(c)) {
                System.out.printf("  ↳ [ROOM STATE] [%s] Client left. Remaining Pipe Connections: %d\n", roomId, clients.size());
            }
        }

        public void broadcast(NetworkEvent event, ClientHandler exclude, int port) {
            for (ClientHandler client : clients) {
                if (client != exclude && client.getServicePort() == port) {
                    client.sendEvent(event);
                }
            }
        }

        public void startNewTrack(int index) {
            long mySessionId;

            synchronized (streamLock) {
                if (this.isPlaying && this.currentTrackIndex == index) {
                    System.out.printf("[PLAYBACK SKIPPED] Room [%s] is already broadcasting this track.\n", roomId);
                    return;
                }

                this.isPlaying = false;

                // Increment the session ID. This instantly invalidates any running loop!
                this.currentSessionId++;
                mySessionId = this.currentSessionId;

                this.currentTrackIndex = index;
                this.currentByteOffset = 0;
                this.sessionChunkLog.clear();
                this.isPlaying = true;
            }

            // Pass the structural session ID directly into the loop context
            try { Thread.sleep(50); } catch (Exception ignored) {}
            streamAudioTrack(index, mySessionId);
        }

        private void streamAudioTrack(int index, long activeSessionId) {
            List<String> rawTracks = getCentralPlaylist();
            if (index < 0 || index >= rawTracks.size()) {
                // Caller (ClientHandler) already validates bounds before this is ever invoked,
                // but guard here too and make sure state doesn't get stuck "playing" a track
                // that doesn't exist.
                synchronized (streamLock) {
                    if (this.currentSessionId == activeSessionId) {
                        this.isPlaying = false;
                        this.currentTrackIndex = -1;
                    }
                }
                return;
            }

            String rawName = rawTracks.get(index).substring(3);
            File audioFile = new File("music", rawName);
            System.out.printf("[AUDIO MASTER LOOP] Room [%s] starting Session [%d] for: '%s'\n", roomId, activeSessionId, rawName);

            try (AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile)) {
                AudioFormat targetFormat = new AudioFormat(44100, 16, 1, true, true);
                try (AudioInputStream decodedAis = AudioSystem.getAudioInputStream(targetFormat, ais)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    while (true) {
                        if (!(this.isPlaying && this.currentSessionId == activeSessionId)) break;

                        bytesRead = decodedAis.read(buffer, 0, buffer.length);
                        if (bytesRead == -1) break;

                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunk, 0, bytesRead);

                        synchronized (streamLock) {
                            // Re-check inside the lock: a new track may have started (or playback
                            // stopped) between the check above and acquiring the lock.
                            if (this.currentSessionId != activeSessionId) break;

                            this.currentByteOffset += bytesRead;
                            this.sessionChunkLog.add(chunk);

                            for (ClientHandler client : clients) {
                                if (client.getServicePort() == STREAM_PORT) {
                                    client.sendEvent(new NetworkEvent("STREAM_CHUNK", "SERVER", roomId, chunk));
                                }
                            }
                        }

                        Thread.sleep(23);
                    }
                }
            } catch (Exception e) {
                System.err.printf("[STREAM EXCEPTION] Room [%s] pipeline broken: %s\n", roomId, e.getMessage());
            } finally {
                synchronized (streamLock) {
                    // Only clear the room variables if this thread is actually the true current master stream
                    if (this.currentSessionId == activeSessionId) {
                        this.isPlaying = false;
                        this.currentTrackIndex = -1;
                        this.currentByteOffset = 0;
                        this.sessionChunkLog.clear();
                        System.out.printf("[AUDIO MASTER LOOP] Room [%s] Session [%d] finished cleanly.\n", roomId, activeSessionId);
                    } else {
                        System.out.printf("[AUDIO CLASH AVOIDED] Old Session [%d] terminated gracefully by engine.\n", activeSessionId);
                    }
                }
            }
        }
    }
}