import os.path

from threading import Thread, Event

import shouty


class Transmitter(Thread):
    def __init__(self, conf, queue):
        super(Transmitter, self).__init__()
        self.stop = Event()
        self.queue = queue

        self.conf = conf
        self.conf_transmitter = conf['TRANSMITTER']
        self.conf_paths = conf['PATHS']

        self.idle_media = os.path.join(
            os.path.dirname(os.path.abspath(__file__)),
            'media', 'idle.ogg')

        self.params = {
            'host': self.conf_transmitter['Host'],
            'port': self.conf_transmitter.getint('Port'),
            'user': self.conf_transmitter['Username'],
            'password': self.conf_transmitter['Password'],
            'format': shouty.Format.OGG,
            'mount': '/' + self.conf_transmitter['Mount']
        }

    def run(self):
        while not self.stop.isSet():

            with shouty.connect(**self.params) as connection:
                if self.queue.empty():
                    connection.send_file(self.idle_media)

                else:
                    uuid = self.queue.get(True)
                    path = os.path.join(self.conf_paths['Tracks'], uuid)

                    connection.send_file(path)

    def join(self, timeout=None):
        self.stop.set()
        super(Transmitter, self).join(timeout)
