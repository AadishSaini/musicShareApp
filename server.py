import socket
from bin.serverClass import *
import threading
sObj = server()

host = ""  # Standard loopback interface address (localhost)
sObj.init_server(5)

while sObj.serverRunning:
	c, addr = sObj.s.accept()
	client_thread = threading.Thread(target=sObj.handleClient, args=(c, addr))
	client_thread.start()

sObj.s.close()