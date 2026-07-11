import java.io.*;
import java.net.*;
import java.util.Scanner;
import javax.sound.sampled.*;

public class MusicShareClient {
    private static SourceDataLine speakers = null;
    private static String username;
    private static String currentRoom; // Made dynamic
    private static volatile boolean isMicMuted = true;

    public static void main(String[] args) {
        username = "User_" + (int)(Math.random() * 1000);
        Scanner scanner = new Scanner(System.in);

        // --- DYNAMIC ROOM SELECTION ON STARTUP ---
        System.out.println("=============================================");
        System.out.println("      WELCOME TO THE MUSICSHARE NETWORK      ");
        System.out.println("=============================================");
        System.out.print("Enter your username (Leave blank for random): ");
        String customName = scanner.nextLine().trim();
        if (!customName.isEmpty()) {
            username = customName;
        }

        System.out.print("Enter the Room Name you want to join (e.g., Lofi, Rock): ");
        currentRoom = scanner.nextLine().trim();
        if (currentRoom.isEmpty()) {
            currentRoom = "Default-Lounge";
        }
        System.out.println("\n[SYSTEM] Provisioning streams for Room: [" + currentRoom + "]...");
        // ------------------------------------------

        try {
            System.out.println("[CLIENT] Connecting Tri-Service Streams...");

            Socket chatSocket = new Socket("localhost", MusicShareServer.CHAT_PORT);
            ObjectOutputStream chatOut = new ObjectOutputStream(chatSocket.getOutputStream());
            ObjectInputStream chatIn = new ObjectInputStream(chatSocket.getInputStream());

            Socket streamSocket = new Socket("localhost", MusicShareServer.STREAM_PORT);
            ObjectOutputStream streamOut = new ObjectOutputStream(streamSocket.getOutputStream());
            ObjectInputStream streamIn = new ObjectInputStream(streamSocket.getInputStream());

            Socket fileSocket = new Socket("localhost", MusicShareServer.FILE_PORT);
            ObjectOutputStream fileOut = new ObjectOutputStream(fileSocket.getOutputStream());
            ObjectInputStream fileIn = new ObjectInputStream(fileSocket.getInputStream());

            chatOut.writeObject(new NetworkEvent("AUTH", username, null, ""));
            chatOut.flush();

            chatOut.writeObject(new NetworkEvent("JOIN_ROOM", username, currentRoom, ""));
            streamOut.writeObject(new NetworkEvent("JOIN_ROOM", username, currentRoom, ""));
            fileOut.writeObject(new NetworkEvent("JOIN_ROOM", username, currentRoom, ""));
            chatOut.flush(); streamOut.flush(); fileOut.flush();

            new Thread(() -> startControlPipeReader(chatIn)).start();
            new Thread(() -> startAudioPipeReader(streamIn)).start();

            streamMicrophoneAudio(streamOut, currentRoom, username);

            System.out.println("\n=============================================");
            System.out.println("     MUSICSHARE LIVE BROADCAST DASHBOARD     ");
            System.out.println("=============================================");
            System.out.println("  Active Room     : " + currentRoom);
            System.out.println("  Active User     : " + username);
            System.out.println("---------------------------------------------");
            System.out.println("  /playlist       - Fetch central tracks catalog");
            System.out.println("  /play <index>   - Stream track from server to your room");
            System.out.println("  /mic on | off   - Toggle microphone streaming channel");
            System.out.println("  /chat <msg>     - Send room text chat");
            System.out.println("=============================================\n");

            while (true) {
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("/playlist")) {
                    chatOut.writeObject(new NetworkEvent("GET_PLAYLIST", username, currentRoom, ""));
                    chatOut.flush();
                } else if (input.startsWith("/play ")) {
                    String indexChoice = input.substring(6);
                    chatOut.writeObject(new NetworkEvent("SYNC_PLAYBACK", username, currentRoom, "PLAY_TRACK_INDEX:" + indexChoice));
                    chatOut.flush();
                } else if (input.equalsIgnoreCase("/mic on")) {
                    isMicMuted = false;
                    System.out.println("[MIC] Mic is LIVE.");
                } else if (input.equalsIgnoreCase("/mic off")) {
                    isMicMuted = true;
                    System.out.println("[MIC] Mic is MUTED.");
                } else if (input.startsWith("/chat ")) {
                    chatOut.writeObject(new NetworkEvent("CHAT", username, currentRoom, input.substring(6)));
                    chatOut.flush();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void broadcastAudioFile(String fileName, ObjectOutputStream out) {
        File audioFile = new File(fileName);
        if (!audioFile.exists()) {
            System.out.println("[ERROR] Audio file not found locally: " + fileName);
            return;
        }

        System.out.println("[BROADCAST] Starting live audio stream of: " + fileName);
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile)) {
            AudioFormat baseFormat = ais.getFormat();
            // Standardize format for streaming transmission
            AudioFormat targetFormat = new AudioFormat(44100, 16, 1, true, true);

            try (AudioInputStream decodedAis = AudioSystem.getAudioInputStream(targetFormat, ais)) {
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = decodedAis.read(buffer, 0, buffer.length)) != -1) {
                    byte[] chunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunk, 0, bytesRead);

                    // Transmit raw audio data directly down the streaming channel
                    out.writeObject(new NetworkEvent("STREAM_CHUNK", username, currentRoom, chunk));
                    out.flush();
                    out.reset();

                    // Throttle transmission to match playback speed (approx 23ms per 4KB chunk at 44.1kHz 16-bit mono)
                    Thread.sleep(23);
                }
                System.out.println("[BROADCAST] Finished streaming audio file.");
            }
        } catch (Exception e) {
            System.err.println("[BROADCAST ERROR] Streaming interrupted: " + e.getMessage());
        }
    }

    private static void startControlPipeReader(ObjectInputStream in) {
        try {
            while (true) {
                NetworkEvent event = (NetworkEvent) in.readObject();
                if ("PLAYLIST_DATA".equals(event.type) && event.playlistTracks != null) {
                    System.out.println("\n--- AVAILABLE CATALOG STREAMS ---");
                    for (String track : event.playlistTracks) {
                        System.out.println("  " + track);
                    }
                    System.out.println("---------------------------------\n> ");
                } else {
                    System.out.printf("\n[CHAT] %s: %s\n> ", event.sender, event.payload);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void startAudioPipeReader(ObjectInputStream in) {
        try {
            while (true) {
                NetworkEvent event = (NetworkEvent) in.readObject();
                if ("STREAM_CHUNK".equals(event.type) && event.binaryData != null) {
                    playAudioChunk(event.binaryData);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void startFilePipeReader(ObjectInputStream in) {}

    private static void streamMicrophoneAudio(ObjectOutputStream out, String roomId, String username) {
        new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();

                byte[] buffer = new byte[1024];
                while (true) {
                    int bytesRead = line.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && !isMicMuted) {
                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                        out.writeObject(new NetworkEvent("STREAM_CHUNK", username, roomId, chunk));
                        out.flush();
                        out.reset();
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private static void playAudioChunk(byte[] data) throws LineUnavailableException {
        if (speakers == null) {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
            speakers = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            speakers.open(format);
            speakers.start();
        }
        speakers.write(data, 0, data.length);
    }
}