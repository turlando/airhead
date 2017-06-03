from threading import Thread, Event
import shouty
from airhead.playlist import EmptyPlaylist
from airhead.config import idle_media


class Broadcaster(Thread):
    def __init__(self, conf, playlist):
        super(Broadcaster, self).__init__()

        self._playlist = playlist
        self._stop_ = Event()

        self._params = {
            'host': conf.get('Host'),
            'port': conf.getint('Port'),
            'user': conf.get('Username'),
            'password': conf.get('Password'),
            'format': shouty.Format.OGG,
            'mount': '/' + conf.get('Mount'),
            'audio_info': {
                'samplerate': '44100',
                'channels': '2',
                'quality': '6'
            }
        }

    def run(self):
        with shouty.connect(**self._params) as connection:
            while not self._stop_.isSet():
                try:
                    uuid = self._playlist.pop()

                except EmptyPlaylist:
                    connection.send_file(idle_media)

                else:
                    path = self._playlist._library.get_path(uuid)
                    connection.send_file(str(path))

    def join(self, timeout=0):
        self._stop_.set()
        super(Broadcaster, self).join(timeout)
