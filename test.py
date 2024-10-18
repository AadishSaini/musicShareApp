# # import socket

# # # Path to the MP3 file (on the client side)
# # file_path = "./congratulation_pewds.mp3"

# # # Set up the socket connection
# # server_address = ('192.168.1.13', 12345)
# # sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
# # sock.connect(server_address)

# # # Open the MP3 file and send it in chunks
# # with open(file_path, 'rb') as audio_file:
# #     while True:
# #         chunk = audio_file.read(1024)  # Read 1 KB of data at a time
# #         if not chunk:
# #             break
# #         sock.sendall(chunk)

# # # Close the socket
# # sock.close()



# # import socket
# # name = socket.gethostbyaddr('192.168.1.13')
# # print(name)













import socket
import pyaudio

chunk = 1024
FORMAT = pyaudio.paInt16
CHANNELS = 1
RATE = 15000
timer = 0

p = pyaudio.PyAudio()


stream = p.open(format = FORMAT,channels = CHANNELS,rate = RATE,input = True,output = True,frames_per_buffer = chunk)

s= socket.socket(socket.AF_INET, socket.SOCK_STREAM)

server = ('127.0.0.1', 8080)

s.connect(server)

print('connected to server')


# receiving = True
# size = c.recv(8)
# print(size.decode())
# while receiving:
# 	package = s.recv(1)
# 	if not package:
# 		receiving = False
# 		print('broke the loop')
# 	else:
# 		size=package
# 		print(package)




receiving = True
file = b''
while receiving:
	data = s.recv(1024)
	if not data:
		receiving = False
		print('broke the loop')
	else:
		print('got: ', data)
		print('now playing it')
		for i in range(0, len(data), chunk):
		    stream.write(data[i:i+chunk])
		file+=data
print(file)






