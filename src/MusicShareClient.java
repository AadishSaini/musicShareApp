import java.io.*;
import java.net.*;
import java.util.Scanner;
import javax.sound.sampled.*;

public class MusicShareClient {
    private static SourceDataLine speakers = null;
    private static String username;
    private static String currentRoom;
    private static volatile boolean isMicMuted = true;

    public static void main(String[] args) {
        username = "User_" + (int)(Math.random() * 1000);
        Scanner scanner = new Scanner(System.in);

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

        Socket chatSocket = null, streamSocket = null, fileSocket = null;
        try {
            System.out.println("[CLIENT] Connecting Tri-Service Streams...");

            chatSocket = new Socket("localhost", MusicShareServer.CHAT_PORT);
            ObjectOutputStream chatOut = new ObjectOutputStream(chatSocket.getOutputStream());
            ObjectInputStream chatIn = new ObjectInputStream(chatSocket.getInputStream());

            streamSocket = new Socket("localhost", MusicShareServer.STREAM_PORT);
            ObjectOutputStream streamOut = new ObjectOutputStream(streamSocket.getOutputStream());
            ObjectInputStream streamIn = new ObjectInputStream(streamSocket.getInputStream());

            fileSocket = new Socket("localhost", MusicShareServer.FILE_PORT);
            ObjectOutputStream fileOut = new ObjectOutputStream(fileSocket.getOutputStream());
            ObjectInputStream fileIn = new ObjectInputStream(fileSocket.getInputStream());

            // --- AUTH HANDSHAKE ---
            // Wait synchronously for the server's AUTH_SUCCESS/AUTH_FAIL before doing anything
            // else, so a duplicate username actually stops the client instead of silently
            // continuing to join a room it was never authenticated for.
            chatOut.writeObject(new NetworkEvent("AUTH", username, null, ""));
            chatOut.flush();

            NetworkEvent authResponse = (NetworkEvent) chatIn.readObject();
            if ("AUTH_FAIL".equals(authResponse.type)) {
                System.out.println("[SYSTEM] " + authResponse.payload);
                System.out.println("[SYSTEM] That username is already in use in this session. Please restart and pick another.");
                chatSocket.close();
                streamSocket.close();
                fileSocket.close();
                return;
            }
            System.out.println("[SYSTEM] " + authResponse.payload);

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
                    String indexChoice = input.substring(6).trim();
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
        } catch (Exception ignored) {
            System.out.println("\n[SYSTEM] Disconnected from server chat channel.");
        }
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