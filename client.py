from initScreen import *
import socket

initS = initS()

ip, port= initS.get_ip_wanted()



s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

s.connect((ip, port))

s.sendall(b"hi")
data = s.recv(1024)

print(f"recved ", data)