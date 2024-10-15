# import socket

# # Path to the MP3 file (on the client side)
# file_path = "./music.mp3"

# # Set up the socket connection
# server_address = ('192.168.1.13', 12345)
# sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
# sock.connect(server_address)

# # Open the MP3 file and send it in chunks
# with open(file_path, 'rb') as audio_file:
#     while True:
#         chunk = audio_file.read(1024)  # Read 1 KB of data at a time
#         if not chunk:
#             break
#         sock.sendall(chunk)

# # Close the socket
# sock.close()



import socket
name = socket.gethostbyaddr('192.168.1.13')
print(name)