import os.path

from threading import Thread, Event

import shouty


class Transmitter(Thread):
    def __init__(self, conf, queue):
        super(Transmitter, self).__init__()
        self.stop = Event()
        self.queue = queue

        self.now_playing = None

        self.conf = conf

        self.idle_media = os.path.join(
            os.path.dirname(os.path.abspath(__file__)),
            'media', 'idle.ogg')

        self.params = {
            'host': self.conf.get('TRANSMITTER', 'Host'),
            'port': self.conf.getint('TRANSMITTER', 'Port'),
            'user': self.conf.get('TRANSMITTER', 'Username'),
            'password': self.conf.get('TRANSMITTER', 'Password'),
            'format': shouty.Format.OGG,
            'mount': '/' + self.conf.get('TRANSMITTER', 'Mount'),
            'audio_info': {
                'samplerate': '44100',
                'channels': '2',
                'quality': '6'
            }
        }

    def run(self):
        with shouty.connect(**self.params) as connection:
            while not self.stop.isSet():

                if self.queue.empty():
                    self.now_playing = None
                    connection.send_file(self.idle_media)

                else:
                    uuid = self.queue.get(True)
                    self.now_playing = uuid
                    path = os.path.join(self.conf.get('PATHS', 'Tracks'), uuid)

                    connection.send_file(path)

    def join(self, timeout=0):
        self.stop.set()
        super(Transmitter, self).join(timeout)
