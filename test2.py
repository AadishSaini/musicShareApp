# # import socket
# # import threading

# # def receive_messages(sock):
# #     while True:
# #         try:
# #             message = sock.recv(1024).decode()
# #             if not message:
# #                 break
# #             print(f"Message from server: {message}")
# #         except Exception as e:
# #             print(f"Error receiving message: {e}")
# #             break

# # # Client code
# # if __name__ == "__main__":
# #     host = "10.0.0.1"  # Server's hostname or IP address
# #     port = int(input('Enter the server port: '))

# #     client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
# #     client_socket.connect((host, port))

# #     threading.Thread(target=receive_messages, args=(client_socket,), daemon=True).start()

# #     while True:
# #         message = input('Enter message: ')
# #         if message == 'quit':
# #             break
# #         client_socket.send(message.encode())

# #     client_socket.close()


# import socket

# s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

# server = ('127.0.0.1', 8080)
# s.bind(server)

# s.listen(5)

# sending = True

# c, addr = s.accept()



# # path_size = os.path.getsize("/path/to/file.mp3")
# # c.send(path_size.encode())






# with open('./music/congratulation_pewds.mp3', 'rb') as f:
#     while sending:
#         package = f.read(1024)
#         if package:
#             c.send(package)
#             print(package)
#             print('sent the package')
#         else:
#             sending = False
# print('completetd sending')





# # while True:
# #         chunk = audio_file.read(1024)  # Read 1 KB of data at a time
# #         if not chunk:
# #             break































import socket

# Server setup
HOST = '127.0.0.1'  # Accept connections on all IPs
PORT = 8080        # Port to listen on

# Open the audio file in binary mode
music_file = "./music/congratulation_pewds.mp3"  # Replace with your audio file

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_socket:
    server_socket.bind((HOST, PORT))
    server_socket.listen(1)
    print(f"Server is listening on {HOST}:{PORT}...")
    
    conn, addr = server_socket.accept()
    with conn:
        print(f"Connected by {addr}")
        with open(music_file, 'rb') as f:
            # Stream the music to the client
            chunk = f.read(1024)
            while chunk:
                conn.sendall(chunk)
                chunk = f.read(1024)
        print("Music streaming completed.")
