import java.io.*;
import java.net.*;
import java.util.Scanner;
import javax.sound.sampled.*;

public class MusicShareClient {
    private static SourceDataLine speakers = null;
    private static String username;
    private static String currentRoom = "Main-Lounge";

    public static void main(String[] args) {
        username = "User_" + (int)(Math.random() * 1000);
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println("[CLIENT] Initializing Tri-Service Sockets...");

            // Connect distinct streams to the 3 unique ports
            Socket chatSocket = new Socket("localhost", MusicShareServer.CHAT_PORT);
            ObjectOutputStream chatOut = new ObjectOutputStream(chatSocket.getOutputStream());
            ObjectInputStream chatIn = new ObjectInputStream(chatSocket.getInputStream());

            Socket streamSocket = new Socket("localhost", MusicShareServer.STREAM_PORT);
            ObjectOutputStream streamOut = new ObjectOutputStream(streamSocket.getOutputStream());
            ObjectInputStream streamIn = new ObjectInputStream(streamSocket.getInputStream());

            Socket fileSocket = new Socket("localhost", MusicShareServer.FILE_PORT);
            ObjectOutputStream fileOut = new ObjectOutputStream(fileSocket.getOutputStream());
            ObjectInputStream fileIn = new ObjectInputStream(fileSocket.getInputStream());

            // Run Authentication
            chatOut.writeObject(new NetworkEvent("AUTH", username, null, ""));
            chatOut.flush();

            // Link all service channels to the active channel space
            chatOut.writeObject(new NetworkEvent("JOIN_ROOM", username, currentRoom, ""));
            streamOut.writeObject(new NetworkEvent("JOIN_ROOM", username, currentRoom, ""));
            fileOut.writeObject(new NetworkEvent("JOIN_ROOM", username, currentRoom, ""));
            chatOut.flush(); streamOut.flush(); fileOut.flush();

            // Fire up async consumer pipes for all network inputs
            new Thread(() -> startControlPipeReader(chatIn)).start();
            new Thread(() -> startAudioPipeReader(streamIn)).start();
            new Thread(() -> startFilePipeReader(fileIn)).start();

            // Begin continuous live audio sampling thread down Port 8081
            streamMicrophoneAudio(streamOut, currentRoom, username);i_Beats.mp3

            System.out.println("\n==============================================");
            System.out.println("  CLIENT WORKSTATION CONTROL DASHBOARD ONLINE");
            System.out.println("  Commands: /chat <msg>  |  /sync <track>");
            System.out.println("==============================================\n");

            while (true) {
                String input = scanner.nextLine();
                if (input.startsWith("/chat ")) {
                    chatOut.writeObject(new NetworkEvent("CHAT", username, currentRoom, input.substring(6)));
                    chatOut.flush();
                } else if (input.startsWith("/sync ")) {
                    NetworkEvent syncEv = new NetworkEvent("SYNC_PLAYBACK", username, currentRoom, "PLAY:" + input.substring(6));
                    syncEv.trackProgressMs = 14200L; // Mock progress offset tracking value
                    syncEv.isPlaying = true;
                    chatOut.writeObject(syncEv);
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
                if ("SYNC_PLAYBACK".equals(event.type)) {
                    System.out.printf("\n[SYNC COMMAND] Global room sync requested by %s -> %s (Timestamp: %d ms)\n> ",
                            event.sender, event.payload, event.trackProgressMs);
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
                    System.out.printf("\n[MEDIA ASS OUT] Direct download asset block complete: %s\n> ", event.payload);
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
                    if (bytesRead > 0) {
                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                        out.writeObject(new NetworkEvent("STREAM_CHUNK", username, roomId, chunk));
                        out.flush();
                        out.reset(); // Clear caching references to optimize memory footprint
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