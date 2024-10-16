from bin.initScreen import *
from bin.handleClient import *

initS = initS()

ip, port= initS.get_ip_wanted()


cObj = handleClient(ip, port)



cObj.handleClient()
