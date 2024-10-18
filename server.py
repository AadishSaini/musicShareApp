import socket
from bin.serverClass import *
import threading

sObj = server()

sObj.init_server(5)

while sObj.serverRunning:
	c, addr = sObj.s.accept()
	c2, addr2 = sObj.s_m.accept()
	c3, addr3 = sObj.s_i.accept()

	client_thread = threading.Thread(target=sObj.handleClient, args=(c, addr, c2, addr2, c3, addr3))
	client_thread.start()

sObj.s.close()

