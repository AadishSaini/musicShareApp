# import socket
# import pyaudio

# # Client setup
# HOST = '127.0.0.1'  # Replace with the server's IP address
# PORT = 8080                 # Port to connect to

# # PyAudio setup
# p = pyaudio.PyAudio()

# # These should match the audio file's properties
# format = pyaudio.paInt16   # Assuming 16-bit audio
# channels = 2               # Stereo audio
# rate = 44100               # Sample rate, adjust if needed

# stream = p.open(format=format,
#                 channels=channels,
#                 rate=rate,
#                 output=True)

# with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as client_socket:
#     client_socket.connect((HOST, PORT))
#     print("Connected to the server.")
    
#     # Receive the music in chunks and play it
#     data = client_socket.recv(1024)
#     while data:
#         stream.write(data)
#         data = client_socket.recv(1024)

# stream.stop_stream()
# stream.close()
# p.terminate()
# print("Music playback completed.")












import socket
import pyaudio
import threading

# Client setup for audio
AUDIO_PORT = 5000
CONTROL_PORT = 5001
SERVER_IP = '127.0.0.1'  # Replace with server's IP

# PyAudio setup for audio playback
p = pyaudio.PyAudio()
stream = p.open(format=pyaudio.paInt16,
                channels=2,
                rate=44100,
                output=True)

# Function to handle audio streaming
def receive_audio():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as audio_socket:
        audio_socket.connect((SERVER_IP, AUDIO_PORT))
        print("Connected to audio stream.")
        data = audio_socket.recv(1024)
        while data:
            stream.write(data)
            data = audio_socket.recv(1024)

# Function to handle control messages
def send_control_message():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as control_socket:
        control_socket.connect((SERVER_IP, CONTROL_PORT))
        print("Connected to control server.")
        while True:
            message = input("Enter control message (type 'exit' to quit): ")
            control_socket.sendall(message.encode('utf-8'))
            if message.lower() == 'exit':
                break
            response = control_socket.recv(1024).decode('utf-8')
            print(f"Server response: {response}")

# Running both functions in parallel
if __name__ == "__main__":
    # Start audio streaming
    threading.Thread(target=receive_audio).start()

    # Start control message communication
    threading.Thread(target=send_control_message).start()
