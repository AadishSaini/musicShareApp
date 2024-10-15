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

        return output

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
        output = self.runCommand (['arp', '-a'])

        string_out = output.stdout.decode("utf-8")

        running = True
        while running:



            print('hello, nearby ip\'s are: ')
            lis = self.get_ips(string_out)[:-1]


            for a in lis:
                print(lis.index(a)+1, ' ', a)

            print("enter q to quit, s to select ip, r to refresh ")
            inp = input()
            if inp == 'q':
                running = False
            elif inp == 's':
                print('enter the number of ip you wish to connect to ')
                n = int(input())+1
                ip_selected = lis[n-2]
                port = int(input('enter the port: '))
                running = False
            elif inp == 'r':
                output = self.runCommand (['arp', '-a'])
                string_out = output.stdout.decode("utf-8")

        if inp == 'q':
            return 'select toh kiya hi nahi'
        else:
            return ip_selected, port
