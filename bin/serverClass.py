import socket
import threading
#import pyautogui
import os
import time
from pydub import AudioSegment
from pydub.utils import make_chunks

class server:
    def __init__(self):
        self.s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.s_m = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.s_i = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        
        self.localIP = '' if (int(input(f'1. To run server locally on localhost \n2. to run the server on local LAN on the IP \'{socket.gethostbyname(socket.gethostname())}\' : ')) == 2) else ('127.0.0.1')
        
        self.port = int(input('Enter the port you wish to proceed with: '))
        self.musicPort = int(input('Enter the music port you wish to proceed with: '))
        self.imagePort = int(input('Enter the image port you wish to proceed with: '))
        
        self.running = True
        self.serverRunning = True
        
        self.audioStartEvent = threading.Event()
        self.imageStartEvent = threading.Event()

        self.musicOption = 0
        self.clients = []
        self.threads = []

    def init_server(self, devices):
        print('\n\nBinding the server ip to the port of chat..')
        self.s.bind((self.localIP, self.port))
        print('Binded the server successfully\n')

        print('now binding the port for music.. ')
        self.s_m.bind((self.localIP, self.musicPort))
        print('binded the music port succssfully.\n')

        print('now binding the port for image.. ')
        self.s_i.bind((self.localIP, self.imagePort))
        print('binded the image port succssfully.\n')

        self.s.listen(5)
        self.s_m.listen(5)
        self.s_i.listen(5)

        # print(f'\nNow listening for upcoming connection requiests on {socket.gethostbyname(socket.gethostname()) if (self.localIP == '') else self.localIP} at port {self.port}')

    def handleClient(self, client, addr, clientMusic, addrMusic, clientImage, ImageAddr):
        print(f"\nNew connection: {addr}")
        self.clients.append([client, addr, True])

        client_thread_send = threading.Thread(target=self.handleClientRecv, args=(client, addr, self.clients[self.clients.index( [client, addr, True]) ] [2] ) ).start()
        client_thread_recv = threading.Thread(target=self.handleClientSend, args=(client, addr, self.clients[self.clients.index( [client, addr, True]) ] [2] ) ).start()
        client_audio_thread = threading.Thread(target=self.handleClientAudio, args = (clientMusic, addrMusic)).start()
        client_image_thread = threading.Thread(target=self.handleClientImage, args = (clientImage, ImageAddr)).start()

        self.threads.append([client_thread_recv, client_thread_send, client_audio_thread, client_image_thread])        

    def handleClientRecv(self, client, addr, running):   
        while running:
            try:
                # print('recving thread running')
                data = client.recv(1024)
                if data.decode() == 'quit':
                    self.clients[self.clients.index( [client, addr, True]) ] [2] = False
                    running = False
                elif not data:
                    pass
                elif data.decode() == 'music list':
                    print('\n\n\n\n\nmusik mode!!!!! Enter which music by "music play x"\n\n\n\n\n')
                    self.musiclist()
                elif 'music play' in data.decode():
                    self.musicOption = int(data.decode()[10:])
                    self.audioStartEvent.set()
                elif 'music resume' == data.decode():
                    self.audioStartEvent.set()
                elif 'music pause' == data.decode():
                    self.audioStartEvent.clear()
                elif 'get image' == data.decode():
                    self.imageStartEvent.set()
                else:
                    print(f"\n[{addr}]: {data.decode()}")

            except ConnectionResetError:
                break

    def handleClientSend(self, client, addr, running):
        while running:
            try:
                data = input('\nEnter message : ')
                if data == 'quit':
                     running = False
                elif not data:
                    pass
                else:
                    client.send(data.encode())
            except ConnectionResetError:
                break


    def musiclist(self):
        musik = os.listdir('./music')
        toSend = ''
        for a in musik:
            print(musik.index(a) + 1, '. ', a)
            toSend+=(str(musik.index(a) + 1)+ '. '+ a +'\n')
        for a in self.clients:
            a[0].send(('Music list received from server: \n'+toSend).encode())



    def handleClientAudio(self, c, addr):
        while self.running:
            self.audioStartEvent.wait() 
            print('Music playing...')
            
            while self.audioStartEvent.is_set():
                musik = os.listdir('./music')  
                print(f'Available music: {musik}')
                req = musik[self.musicOption - 1]  
                print(f'Sending file: {req}')
                
                
                song = AudioSegment.from_file('./music/' + req)
                audio_chunks = make_chunks(song.raw_data, 1024)
                
                for chunk in audio_chunks:
                    if not self.audioStartEvent.is_set():
                        print("Music paused")
                        old_option = self.musicOption 
                        
                        
                        while not self.audioStartEvent.is_set():  
                            time.sleep(1)
                        
                        
                        if old_option != self.musicOption:
                            req = musik[self.musicOption - 1] 
                            print(f"Switching to {req}")
                            song = AudioSegment.from_file('./music/' + req)
                            audio_chunks = make_chunks(song.raw_data, 1024)
                            print('Audio chunks updated due to music change')
                        
                        print("Resuming music")
                        continue 

                    try:
                        c.sendall(chunk)
                    except BrokenPipeError:
                        print(f"Connection lost with {addr}")
                        break

               
                self.audioStartEvent.clear()
                print(f"Finished sending {req}")

            
    def handleClientImage(self, c, addr):
        self.imageStartEvent.wait()
        while self.running:
            image = './images/aaaaaaaa.jpg'
            with open(image, 'rb') as f:
                sending = True
                while sending:
                    data = f.read(1024)
                    if not data:
                        print('file finished, breaking loop')
                        break
                    print(data)
                    c.send(data)
            print('sent the file')

    # def playMusic(self, option):
    #     print('wanted music is ', option)
    #     musik = os.listdir('./music')
    #     time.sleep(2)
    #     print('sending file now')
    #     req = musik[option-1]
    #     with open('./music/'+req, 'rb') as f:
    #         while True:
    #             data = f.read(1)
    #             if not data:
    #                 break
    #             for a in self.clients:
    #                 a[0].send(data)
    #         print('finished the file')
