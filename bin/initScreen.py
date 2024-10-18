import subprocess
import socket

class initS:
    def __init__(self):
        self.s = socket.socket()

    def runCommand (self, command):
        output=subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE)

        if output.returncode != 0:
            raise RuntimeError(
                output.stderr.decode("utf-8"))

        string_out = output.stdout.decode("utf-8")
        print(string_out)
        return string_out

    def get_ips(self, string_out):
        lis = string_out.split('\n')
        ip_nearby = []

        for a in lis:
            got = False

            ip = ''
            for i in lis[lis.index(a)]:
                if i == ')':
                    got = False
                if got:
                    ip += i
                if i == '(':
                    got = True
            ip_nearby.append(ip)


        return ip_nearby

    def get_ip_wanted(self):
        string_out = self.runCommand(['arp', '-a'])

        running = True
        while running:


            print('hello, nearby ip\'s are: \n')
            lis = self.get_ips(string_out)[:-1]


            for a in lis:
                print(lis.index(a)+1, ' ', a)

            inp = input("\n\nSelect one option:\n-q to quit,\n-s to select ip,\n-r to refresh \n-l to run the client for localhost\n-m to enter the hostIP manually: ")
            
            if inp == 'q':
                running = False
            elif inp == 'l':
                ip_selected = '127.0.0.1'
                running = False
            elif inp == 'm':
                ip_selected = input("\n\nEnter the manual ip: ")
                running = False
            elif inp == 's':
                n = int(input(('\nenter the index of ip you wish to connect to: ')))
                n +=1
                ip_selected = lis[n-2]

                running = False
            elif inp == 'r':
                output = self.runCommand (['arp', '-a'])

        if inp == 'q':
            return 'select toh kiya hi nahi'
        else:
            port = int(input('enter the port: '))
            musicPort = int(input('enter the music port: '))
            imagePort = int(input('enter the image port: '))
            return ip_selected, port, musicPort, imagePort
