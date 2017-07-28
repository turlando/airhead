from aiohttp.web import json_response
from tempfile import NamedTemporaryFile as TemporaryFile

from airhead.library import TrackNotFoundError
from airhead.playlist import DuplicateTrackError
from airhead.transcoder import IllegalCodecError


async def store_file(reader):
    f = await reader.next()

    with TemporaryFile(delete=False) as fp:
        while True:
            chunk = await f.read_chunk()

            if not chunk:
                break

            fp.write(chunk)

    return fp.name


async def info(request):
    conf = request.app['config']
    return json_response({
        'name': conf.get('INFO', 'Name'),
        'greet_message': conf.get('INFO', 'GreetMessage'),
        'stream_url': "http://{}:{}/{}"
                      .format(conf.get('ICECAST', 'Host'),
                              conf.get('ICECAST', 'Port'),
                              conf.get('ICECAST', 'Mount'))
    })


async def library_query(request):
    # If the request URL has not the '?q=<query>' parameter
    # catch the error and set q to None in order to call
    # Library.query(q=None) and get all the stored tracks.
    q = request.query.get('q', None)

    tracks = request.app['library'].query(q)
    return json_response({'tracks': tracks}, status=200)


async def library_get(request):
    uuid = request.match_info['uuid']

    try:
        track = request.app['library'].get_track(uuid)

    except TrackNotFoundError as e:
        return json_response({
            'err': 'uuid_not_valid',
            'msg': 'No track found with such UUID.'
        }, status=400)

    else:
        return json_response(track, status=200)


async def library_add(request):
    reader = await request.multipart()
    path = await store_file(reader)

    try:
        track = request.app['library'].add(path, delete=True)

    except FileNotFoundError:
        return json_response({
            'err': 'upload_failed',
            'msg': 'This is strange.'
        }, status=500)

    except IllegalCodecError as e:
        return json_response({
            'err': 'illegal_codec',
            'msg': 'This kind of file is not supported.'
        }, status=400)

    else:
        return json_response({'track': track}, status=200)


async def playlist_query(request):
    return json_response({
        'current': request.app['playlist'].current_track,
        'next': request.app['playlist'].next_tracks
    }, status=200)


async def playlist_add(request):
    uuid = request.match_info['uuid']

    try:
        request.app['playlist'].put(uuid)

    except TrackNotFoundError:
        return json_response({
            'err': 'track_not_found',
            'msg': 'No track found with such UUID.'
        }, status=400)

    except DuplicateTrackError:
        return json_response({
            'err': 'duplicate',
            'msg': 'The track is already present in the playlist.'
        }, status=400)

    else:
        return json_response({}, status=200)


async def playlist_remove(request):
    uuid = request.match_info['uuid']

    try:
        request.app['playlist'].remove(uuid)

    except TrackNotFoundError:
        return json_response({
            'err': 'track_not_found',
            'msg': 'No track found with such UUID.'
        }, status=400)

    else:
        return json_response({}, status=200)


async def playlist_skip(request):
    uuid = request.match_info['uuid']
    request.app['broadcaster'].skip(uuid)
    return json_response({}, status=200)
