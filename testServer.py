# import socket
# from pydub import AudioSegment
# from pydub.utils import make_chunks

# # Server setup
# HOST = '127.0.0.1'  # Accept connections on all IPs
# PORT = 8080       # Port to listen on

# # Load and decode the audio file
# music_file = "./music/congratulation_pewds.mp3"  # Replace with your MP3 file
# audio = AudioSegment.from_mp3(music_file)

# # Split the audio into chunks of 1024 bytes
# chunk_size = 1024  # Adjust chunk size if needed
# chunks = make_chunks(audio.raw_data, chunk_size)

# with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_socket:
#     server_socket.bind((HOST, PORT))
#     server_socket.listen(1)
#     print(f"Server is listening on {HOST}:{PORT}...")
    
#     conn, addr = server_socket.accept()
#     with conn:
#         print(f"Connected by {addr}")
#         for chunk in chunks:
#             conn.sendall(chunk)  # Send each chunk
#         print("Music streaming completed.")














import socket
import threading
from pydub import AudioSegment
from pydub.utils import make_chunks

# Load and decode audio file
audio = AudioSegment.from_mp3("./music/congratulation_pewds.mp3")
chunk_size = 1024
audio_chunks = make_chunks(audio.raw_data, chunk_size)

# Define the ports
AUDIO_PORT = 5000
CONTROL_PORT = 5001

# Function to handle audio streaming
def handle_audio_stream(client_socket):
    print("Audio stream connected.")
    for chunk in audio_chunks:
        client_socket.sendall(chunk)
    client_socket.close()
    print("Audio streaming completed.")

# Function to handle control messages
def handle_control(client_socket):
    print("Control connection established.")
    while True:
        message = client_socket.recv(1024).decode('utf-8')
        if message.lower() == 'exit':
            print("Closing control connection.")
            break
        print(f"Received control message: {message}")
        client_socket.sendall(b'Control message received')
    client_socket.close()

# Function to start server and listen on different ports
def start_server():
    # Start audio stream server
    audio_server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    audio_server.bind(('127.0.0.1', AUDIO_PORT))
    audio_server.listen(1)
    print(f"Audio server listening on port {AUDIO_PORT}...")

    # Start control message server
    control_server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    control_server.bind(('127.0.0.1', CONTROL_PORT))
    control_server.listen(1)
    print(f"Control server listening on port {CONTROL_PORT}...")

    while True:
        # Accept connections for audio
        audio_conn, audio_addr = audio_server.accept()
        threading.Thread(target=handle_audio_stream, args=(audio_conn,)).start()

        # Accept connections for control messages
        control_conn, control_addr = control_server.accept()
        threading.Thread(target=handle_control, args=(control_conn,)).start()

if __name__ == "__main__":
    start_server()
