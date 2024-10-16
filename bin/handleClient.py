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
                elif 'music play' in inp:
                    self.musicMode = True
                    self.runMusicMode()
                elif not inp:
                    pass
                else:
                    self.s.sendall(inp.encode())
            except ConnectionResetError:
                break

    def runMusicMode(self):
        print('music called')
        buf = ''
        receiving = True
        while receiving:
            received_data = self.s.recv(1)
            if received_data:
                receiving = True
                buf += received_data
                print(received_data)
            else:
                receiving = False
        print(buf)
