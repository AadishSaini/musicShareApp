import java.io.*;
import java.net.*;
import java.util.Scanner;
import javax.sound.sampled.*;

public class MusicShareClient {
    private static SourceDataLine speakers = null;
    private static String username;
    private static String currentRoom = "Main-Lounge";

    // Core Client Control Variables
    private static volatile boolean isMicMuted = true; // Muted by default until allowed by client

    public static void main(String[] args) {
        username = "User_" + (int)(Math.random() * 1000);
        Scanner scanner = new Scanner(System.in);

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
            new Thread(() -> startFilePipeReader(fileIn)).start();

            // Run the audio collection hardware thread (will evaluate the mic toggle flag dynamically)
            streamMicrophoneAudio(streamOut, currentRoom, username);

            System.out.println("\n=============================================");
            System.out.println("     MUSICSHARE CENTRAL DASHBOARD CONSOLE     ");
            System.out.println("=============================================");
            System.out.println("  /playlist        - Fetch central shared tracks catalog");
            System.out.println("  /sync <track_no> - Select and sync-play track out of repository");
            System.out.println("  /mic on          - Enable your microphone voice streaming channel");
            System.out.println("  /mic off         - Mute/Stop microphone capture transmission");
            System.out.println("  /chat <msg>      - Send room text chat packet");
            System.out.println("=============================================\n");

            while (true) {
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("/playlist")) {
                    System.out.println("[REQUEST] Fetching central track list data from server indices...");
                    chatOut.writeObject(new NetworkEvent("GET_PLAYLIST", username, currentRoom, ""));
                    chatOut.flush();
                } else if (input.startsWith("/sync ")) {
                    String trackChoice = input.substring(6);
                    NetworkEvent syncEv = new NetworkEvent("SYNC_PLAYBACK", username, currentRoom, "PLAY_TRACK_INDEX:" + trackChoice);
                    syncEv.trackProgressMs = 0L;
                    syncEv.isPlaying = true;
                    chatOut.writeObject(syncEv);
                    chatOut.flush();
                    System.out.println("[MEDIA SYSTEM] Playback invocation update broadcasted out to room clients.");
                } else if (input.equalsIgnoreCase("/mic on")) {
                    isMutedMicToggle(false);
                } else if (input.equalsIgnoreCase("/mic off")) {
                    isMutedMicToggle(true);
                } else if (input.startsWith("/chat ")) {
                    chatOut.writeObject(new NetworkEvent("CHAT", username, currentRoom, input.substring(6)));
                    chatOut.flush();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void isMutedMicToggle(boolean setMuted) {
        isMicMuted = setMuted;
        if (isMicMuted) {
            System.out.println("[MIC CONFIG] Microphone input transmission is currently STOPPED/MUTED.");
        } else {
            System.out.println("[MIC CONFIG] Microphone is LIVE. Transmitting voice tracking frames...");
        }
    }

    private static void startControlPipeReader(ObjectInputStream in) {
        try {
            while (true) {
                NetworkEvent event = (NetworkEvent) in.readObject();
                if ("PLAYLIST_DATA".equals(event.type) && event.playlistTracks != null) {
                    System.out.println("\n--- CENTRAL SYSTEM REPOSITORY PLAYLIST ---");
                    for (String track : event.playlistTracks) {
                        System.out.println("  " + track);
                    }
                    System.out.println("------------------------------------------\n> ");
                } else if ("SYNC_PLAYBACK".equals(event.type)) {
                    System.out.printf("\n[SYNC SIGNAL] Synchronization carousel event: %s playing for all clients.\n> ", event.payload);
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
                if ("STREAM_CHUNK".equals(event.type)) {
                    playAudioChunk(event.binaryData);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void startFilePipeReader(ObjectInputStream in) {
        try {
            while (true) {
                NetworkEvent event = (NetworkEvent) in.readObject();
                if ("MEDIA_TRANSFER".equals(event.type)) {
                    System.out.printf("\n[DOWNLOAD] Download verification block completed: %s\n> ", event.payload);
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
                    // Check if mic toggle variable allows data processing output
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