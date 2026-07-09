import java.io.*;
import java.net.*;
import javax.sound.sampled.*;

public class MusicShareClient {
    private static SourceDataLine speakers = null;

    public static void main(String[] args) {
        String username = "User_" + (int)(Math.random() * 1000);
        String targetRoom = "Main-Lounge";

        try {
            Socket socket = new Socket("localhost", 8080);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            System.out.println("[CLIENT] Connected! Authenticating as: " + username);
            out.writeObject(new NetworkEvent("AUTH", username, null, ""));
            out.flush();

            // Background Network Reader Thread
            new Thread(() -> {
                try {
                    while (true) {
                        NetworkEvent event = (NetworkEvent) in.readObject();
                        if ("STREAM_CHUNK".equals(event.type)) {
                            playAudioChunk(event.binaryData);
                        } else {
                            System.out.println("\n[" + event.type + "] " + event.sender + ": " + event.payload);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Disconnected from server.");
                }
            }).start();

            Thread.sleep(1000);
            System.out.println("[CLIENT] Joining room: " + targetRoom);
            out.writeObject(new NetworkEvent("JOIN_ROOM", username, targetRoom, ""));
            out.flush();

            // Automatically kick off mic streaming
            streamMicrophoneAudio(out, targetRoom, username);

            // Keep alive loop
            while (true) { Thread.sleep(1000); }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void streamMicrophoneAudio(ObjectOutputStream out, String roomId, String username) {
        new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();

                byte[] buffer = new byte[2048];
                System.out.println("[MIC] Streaming live microphone input to the room...");

                while (true) {
                    int bytesRead = line.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                        out.writeObject(new NetworkEvent("STREAM_CHUNK", username, roomId, chunk));
                        out.flush();
                    }
                }
            } catch (Exception e) {
                System.err.println("Mic streaming failed: " + e.getMessage());
            }
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