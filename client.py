from bin.initScreen import *
import threading
import socket
from bin.handleClient import *

def start_client(host, port):
    client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client.connect((host, port))

    # try:
    #     while True:
    #         message = input("Enter message: ")
    #         if message.lower() == 'exit':
    #             break
    #         client.send(message.encode())
    #         response = client.recv(1024)
    #         print(f"[SERVER]: {response.decode()}")
    # finally:
    #     client.close()






initS = initS()

ip, port= initS.get_ip_wanted()


cObj = handleClient(ip, port)


# while cObj.running:
# server_recv_thread = threading.Thread(target=cObj.handleClient)
# server_recv_thread.start()
cObj.handleClient()
# start_client(ip, port)