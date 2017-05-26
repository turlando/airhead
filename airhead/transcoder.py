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


CODECS = {
    OggVorbis: _vorbis_tags,
    FLAC: _vorbis_tags,
    MP3: _id3_tags
}


class IllegalCodecError(Exception):
    pass


def _get_tags(f):
    media_file = mutagen.File(str(f))
    codec = type(media_file)

    try:
        get_tags = CODECS[codec]
    except KeyError as e:
        raise IllegalCodecError from e
    else:
        tags = get_tags(media_file)

    return tags


def _run_ffmpeg(in_, out, exe='ffmpeg'):
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


def transcode(in_path, out_path, uuid, on_complete, delete=False):
    track = {uuid: _get_tags(in_path)}

    def worker():
        _run_ffmpeg(in_path, out_path)

        if delete:
            in_path.unlink()

        on_complete(track)

    t = Thread(target=worker)
    t.start()
