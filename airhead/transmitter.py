import os.path
from threading import Thread, Event
import shouty
from airhead.config import get_config, idle_media


conf = get_config()


class Transmitter(Thread):
    def __init__(self, queue):
        super(Transmitter, self).__init__()
        self.stop = Event()
        self.queue = queue

        self.params = {
            'host': conf.get('TRANSMITTER', 'Host'),
            'port': conf.getint('TRANSMITTER', 'Port'),
            'user': conf.get('TRANSMITTER', 'Username'),
            'password': conf.get('TRANSMITTER', 'Password'),
            'format': shouty.Format.OGG,
            'mount': '/' + conf.get('TRANSMITTER', 'Mount'),
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
                    self.queue.now_playing = None
                    connection.send_file(idle_media)

                else:
                    uuid = self.queue.get(True)
                    path = os.path.join(conf.get('PATHS', 'Tracks'), uuid)

                    connection.send_file(path)

    def join(self, timeout=0):
        self.stop.set()
        super(Transmitter, self).join(timeout)
