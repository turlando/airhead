from threading import Thread
from ffmpy import FFmpeg

import mutagen
from mutagen.oggvorbis import OggVorbis
from mutagen.flac import FLAC
from mutagen.mp3 import MP3


def _vorbis_tags(f):
    return {
        'title': '; '.join(f.tags['title']),
        'artist': '; '.join(f.tags['artist']),
        'album': '; '.join(f.tags['album'])
    }


def _id3_tags(f):
    return {
        'title': '; '.join(f.tags['TIT2']),
        'artist': '; '.join(f.tags['TOPE']),
        'album': '; '.join(f.tags['TALB'])
    }


def _get_tags(f):
    media_file = mutagen.File(str(f))
    codec = type(media_file)

    try:
        get_tags = CODECS[codec]
    except KeyError:
        raise Exception("Codec not supported.")
    finally:
        tags = get_tags(media_file)

    return tags


def _transcode(in_, out, exe='ffmpeg'):
    ff = FFmpeg(
        executable=exe,
        inputs={str(in_): None},
        outputs={str(out): [
            '-map', '0:0',
            '-f', 'ogg',
            '-c:a:0', 'libvorbis',
            '-q:a:0', '6'
            ]})
    ff.run()


CODECS = {
    OggVorbis: _vorbis_tags,
    FLAC: _vorbis_tags,
    MP3: _id3_tags
}


class Transcoder:
    def __init__(self, library):
        self._library = library

    def _process(self, path, uuid, delete):
        d = {uuid: _get_tags(path)}

        dest_path = self._library.get_path(uuid)
        _transcode(path, dest_path)

        if delete:
            path.unlink()

        self._library._add(d)

    def process(self, path, uuid, delete=False):
        def f():
            self._process(path, uuid, delete)

        t = Thread(target=f)
        t.start()
