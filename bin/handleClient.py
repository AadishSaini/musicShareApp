import socket
import threading
import pyaudio
import time



class handleClient:
    def __init__(self, host, port, musicPort, imagePort):
        self.hostIP = host
        self.hostPort = port
        self.musicPort = musicPort
        self.imagePort = imagePort

        print(f'\n\nip wanted are {self.hostIP} and pport is {self.hostPort }')
        self.s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.s_m = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.s_i = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

        print('\nconnecting to the server....')
        self.s.connect((self.hostIP, self.hostPort))
        print('\nconnected.\n')

        print('connecting to music port now')
        self.s_m.connect((self.hostIP, self.musicPort))
        print('connected to music port aswell\n\n')

        print('connecting to image port now')
        self.s_i.connect((self.hostIP, self.imagePort))
        print('connected to image port aswell\n\n')

        self.p = pyaudio.PyAudio()
        self.stream = self.p.open(format=pyaudio.paInt16,
                        channels=2,
                        rate=44100,
                        output=True)

        self.running = True
    
    def handleClient(self):
        client_side_thread_send = threading.Thread(target=self.sendFunction).start()
        client_side_thread_recv = threading.Thread(target=self.recvFunction).start()
        client_audio_manager_thread = threading.Thread(target=self.runMusicMode).start()
        client_image_manager_thread = threading.Thread(target=self.imageThread).start()
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

    def reset_audio_stream():
        try:
            stream.stop_stream()  
            stream.close()  
            time.sleep(0.1) 
            stream = pyaudio.PyAudio().open(format=pyaudio.paInt16,
                                            channels=2,
                                            rate=44100,
                                            output=True)
        except Exception as e:
            print(f"Error resetting the audio stream: {e}")


    def runMusicMode(self):
        print('music thread initiated')
        data = self.s_m.recv(1024)
        while self.running:
            try:
                data = self.s_m.recv(1024)
                if data:
                    self.stream.write(data)
                else:
                    break  
            except ConnectionResetError:
                break

    def imageThread(self):
        buf = b''
        while self.running:
            try:
                data = self.s_i.recv(1024)
                if data:
                    print(data)
                    buf+=data
                else:
                    print('recved file')
                    break  
            except ConnectionResetError:
                break
        print(buf)
        with open('imageRecved.jpg', 'wb') as f:
            f.write(buf)