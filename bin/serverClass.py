import socket
import threading
#import pyautogui
import os
import time

class server:
    def __init__(self):
        self.s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.port = int(input('enter the port you wish to proceed with '))
        self.running = True
        self.serverRunning = True
        self.threadNotStopped = True
        self.clients = []
        self.threads = []

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
        self.threads.append([client_thread_recv, client_thread_send])

    def handleClientRecv(self, client, addr, running):   
        while running and self.threadNotStopped:
            try:
                data = client.recv(1024)
                if data.decode() == 'quit':
                    self.clients[self.clients.index( [client, addr, True]) ] [2] = False
                elif not data:
                    pass
                elif data.decode() == 'music list':
                    print('\n\n\n\n\nmusik mode!!!!! Enter which music by "music play x"\n\n\n\n\n')
                    music_list_thread = threading.Thread(target=self.musiclist())
                    music_list_thread.start()
                elif 'music play' in data.decode():
                    self.playMusic(int(data.decode()[10:]))
                else:
                    print(f"\n[{addr}]: {data.decode()}")
                    #pyautogui.write("\n")
                # client.sendall(data)
            except ConnectionResetError:
                break



    def handleClientSend(self, client, addr, running):
        while running and self.threadNotStopped:
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

    def musiclist(self):
        musik = os.listdir('./music')
        toSend = ''
        for a in musik:
            print(musik.index(a) + 1, '. ', a)
            toSend+=(str(musik.index(a) + 1)+ '. '+ a)
        for a in self.clients:
            a[0].send(toSend.encode())

    def playMusic(self, option):
        # print('wanted music is ', option)
        musik = os.listdir('./music')
        self.threadNotStopped = False
        time.sleep(2)
        print('sending file now')
        req = musik[option-1]
        with open('./music/'+req, 'rb') as f:
            while True:
                data = f.read(1)
                if not data:
                    break
                for a in self.clients:
                    a[0].send(data)
            # print('finished the file')