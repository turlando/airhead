import os
from datetime import datetime

from threading import Thread, Event
from queue import Empty

import mutagen
from mutagen.oggvorbis import OggVorbis
from mutagen.mp3 import MP3, EasyMP3

import json

from ffmpy import FFmpeg


def get_vorbis_tags(path):
    f = OggVorbis(path)
    return {
        'title': '; '.join(f.tags['title']),
        'artist': '; '.join(f.tags['artist']),
        'album': '; '.join(f.tags['album'])
    }


def get_mp3_tags(path):
    f = EasyMP3(path)
    return {
        'title': '; '.join(f.tags['title']),
        'artist': '; '.join(f.tags['artist']),
        'album': '; '.join(f.tags['album'])
    }


def save_tags(in_path, out_path, uuid):
    in_file = os.path.join(in_path, uuid)
    json_file = '.'.join([os.path.join(out_path, uuid), 'json'])

    f = mutagen.File(in_file)
    if isinstance(f, OggVorbis):
        tags = get_vorbis_tags(in_file)
    elif isinstance(f, MP3):
        tags = get_mp3_tags(in_file)

    tags['import_date'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    with open(json_file, 'w') as fp:
        json.dump(tags, fp)


def transcode(in_path, out_path, uuid):
    in_file = os.path.join(in_path, uuid)
    out_file = os.path.join(out_path, uuid)

    ff = FFmpeg(
        inputs={in_file: None},
        outputs={out_file: [
            '-map', '0:0',
            '-f', 'ogg',
            '-c:a:0', 'libvorbis',
            '-q:a:0', '6'
            ]})
    ff.run()


class Transcoder(Thread):
    def __init__(self, conf, queue):
        super(Transcoder, self).__init__()
        self.stop = Event()
        self.queue = queue

        self.conf = conf
        self.conf_paths = conf['PATHS']

    def run(self):
        while not self.stop.isSet():

            try:
                uuid = self.queue.get(True)
                in_path = self.conf_paths['Upload']
                out_path = self.conf_paths['Tracks']

                transcode(in_path, out_path, uuid)
                save_tags(in_path, out_path, uuid)

                os.remove(os.path.join(in_path, uuid))

            except Empty:
                continue

    def join(self, timeout=0):
        self.stop.set()
        super(Transcoder, self).join(timeout)
