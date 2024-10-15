import socket
import threading
import pyautogui

class server:
    def __init__(self):
        self.s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.port = int(input('enter the port you wish to proceed with '))
        self.running = True
        self.serverRunning = True
        self.clients = []

    def init_server(self, devices):
        self.s.bind(('', self.port))
        self.s.listen(5)

    def handleClient(self, client, addr):
        print(f"\nNew connection: {addr}")
        self.clients.append([client, addr, True])
        client_thread_send = threading.Thread(target=self.handleClientRecv, args=(client, addr, self.clients[self.clients.index( [client, addr, True]) ] [2] ) )
        client_thread_recv = threading.Thread(target=self.handleClientSend, args=(client, addr, self.clients[self.clients.index( [client, addr, True]) ] [2] ) )
        client_thread_send.start()
        client_thread_recv.start()

    def handleClientRecv(self, client, addr, running):   
        while running:
            try:
                data = client.recv(1024)
                if data.decode() == 'quit':
                    self.clients[self.clients.index( [client, addr, True]) ] [2] 
                elif not data:
                    pass
                else:
                    print(f"\n[{addr}]: {data.decode()}")
                    pyautogui.write("\n")
                # client.sendall(data)
            except ConnectionResetError:
                break
        print(f"Connection closed: {addr}")
        client.close()

    def handleClientSend(self, client, addr, running):
        while running:
            try:
                data = input('\nEnter message : ')
                if data == 'quit':
                    self.clients[self.clients.index( [client, addr, True]) ] [2] = False
                elif not data:
                    pass
                else:
                    client.send(data.encode())
                # client.sendall(data)
            except ConnectionResetError:
                break
        print(f"Connection closed: {addr}")
        client.close()