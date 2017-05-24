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


CODECS = {
    OggVorbis: _vorbis_tags,
    FLAC: _vorbis_tags,
    MP3: _id3_tags
}


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


def transcode(library, path, uuid, delete=False):
    def worker():
        track = {uuid: _get_tags(path)}

        dest_path = library.get_path(uuid)
        _run_ffmpeg(path, dest_path)

        if delete:
            path.unlink()

        library._add(track)

    t = Thread(target=worker)
    t.start()
