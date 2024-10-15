import socket
import threading

class handleClient:
    def __init__(self, host, port):
        self.s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.s.connect((host, port))
        self.running = True
    
    def handleClient(self):
        client_side_thread_send = threading.Thread(target=self.sendFunction)
        client_side_thread_recv = threading.Thread(target=self.recvFunction)
        client_side_thread_send.start()
        client_side_thread_recv.start()

    def sendFunction(self):
        while self.running:
            try:
                data = self.s.recv(1024)
                if not data:
                    pass
                elif data == 'exit':
                    self.running = False
                else:
                    print('\n[SERVER] : ', data.decode())
                    # pyautogui.write('\n')
            except ConnectionResetError:
                break
    
    def recvFunction(self):
        while self.running:
            try:
                inp = input("\nEnter Message: ")
                if inp == 'exit':
                    self.running = False
                elif not inp:
                    pass
                else:
                    self.s.sendall(inp.encode())
            except ConnectionResetError:
                break
