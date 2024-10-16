import socket
import threading

def receive_messages(sock):
    while True:
        try:
            message = sock.recv(1024).decode()
            if not message:
                break
            print(f"Message from server: {message}")
        except Exception as e:
            print(f"Error receiving message: {e}")
            break

# Client code
if __name__ == "__main__":
    host = "10.0.0.1"  # Server's hostname or IP address
    port = int(input('Enter the server port: '))

    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect((host, port))

    threading.Thread(target=receive_messages, args=(client_socket,), daemon=True).start()

    while True:
        message = input('Enter message: ')
        if message == 'quit':
            break
        client_socket.send(message.encode())

    client_socket.close()
