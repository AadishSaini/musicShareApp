from bin.initScreen import *
from bin.handleClient import *

initS = initS()

ip, port, musicPort, imagePort = initS.get_ip_wanted()




cObj = handleClient(ip, port, musicPort, imagePort)



cObj.handleClient()
