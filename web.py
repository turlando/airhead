from aiohttp import web

import logging
from tempfile import NamedTemporaryFile as TemporaryFile

from airhead.config import get_config
from airhead.library import Library, TrackNotFoundError
from airhead.playlist import Playlist, DuplicateTrackError
from airhead.transcoder import IllegalCodecError
from airhead.broadcaster import Broadcaster

log = logging.getLogger('airhead')


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
    return web.json_response({
        'name': app['config'].get('INFO', 'Name'),
        'greet_message': app['config'].get('INFO', 'GreetMessage'),
        'stream_url': "http://{}:{}/{}".format(
            app['config'].get('ICECAST', 'Host'),
            app['config'].get('ICECAST', 'Port'),
            app['config'].get('ICECAST', 'Mount'))
    })


async def library_query(request):
    try:
        q = request.query['q']
    except KeyError:
        # If the request URL has not the '?q=<query>' parameter
        # catch the error and set q to None in order to call
        # Library.query(q=None) and get all the stored tracks.
        q = None

    tracks = app['library'].query(q)
    return web.json_response({'tracks': tracks}, status=200)


async def library_get(request):
    uuid = request.match_info['uuid']

    try:
        track = app['library'].get_track(uuid)

    except TrackNotFoundError as e:
        return web.json_response({
            'err': 'uuid_not_valid',
            'msg': 'No track found with such UUID.'
        }, status=400)

    else:
        return web.json_response(track, status=200)


async def library_add(request):
    reader = await request.multipart()
    path = await store_file(reader)

    try:
        track = app['library'].add(path, delete=True)

    except FileNotFoundError:
        return web.json_response({
            'err': 'upload_failed',
            'msg': 'This is strange.'
        }, status=500)

    except IllegalCodecError as e:
        return web.json_response({
            'err': 'illegal_codec',
            'msg': 'This kind of file is not supported.'
        }, status=400)

    else:
        return web.json_response({'track': track}, status=200)


async def playlist_query(request):
    return web.json_response({
        'current': app['playlist'].current_track,
        'next': app['playlist'].next_tracks
    }, status=200)


async def playlist_add(request):
    uuid = request.match_info['uuid']

    try:
        app['playlist'].put(uuid)

    except TrackNotFoundError:
        return web.json_response({
            'err': 'track_not_found',
            'msg': 'No track found with such UUID.'
        }, status=400)

    except DuplicateTrackError:
        return web.json_response({
            'err': 'duplicate',
            'msg': 'The track is already present in the playlist.'
        }, status=400)

    else:
        return web.json_response({}, status=200)


async def playlist_remove(request):
    uuid = request.match_info['uuid']

    try:
        app['playlist'].remove(uuid)

    except TrackNotFoundError:
        return web.json_response({
            'err': 'track_not_found',
            'msg': 'No track found with such UUID.'
        }, status=400)

    else:
        return web.json_response({}, status=200)


async def websocket_shutdown(app):
    log.debug("Disconnecting {} clients.".format(len(app['websockets'])))
    for client in app['websockets']:
        await client.close()


async def websocket(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    try:
        log.debug("Websocket client connected.")
        request.app['websockets'].append(ws)
        async for msg in ws:
            pass
        return ws

    finally:
        log.debug("Websocket client disconnected.")
        request.app['websockets'].remove(ws)


def broadcast_library_update():
    for client in app['websockets']:
        client.send_json({'update': 'library'})


def broadcast_playlist_update():
    for client in app['websockets']:
        client.send_json({'update': 'playlist'})


def broadcaster_shutdown(app):
    app['broadcaster'].join()


app = web.Application()

app['config'] = get_config()
app['websockets'] = list()

app['library'] = Library(app['config'].get('GENERAL', 'Library'),
                         notify=broadcast_library_update)
app['playlist'] = Playlist(app['library'], notify=broadcast_playlist_update,
                           auto_dj=app['config'].getboolean('GENERAL', 'AutoDj'))
app['broadcaster'] = Broadcaster(app['config']['ICECAST'], app['playlist'])

app.router.add_route('GET', '/api/info', info)
app.router.add_route('GET', '/api/library', library_query)
app.router.add_route('GET', '/api/library/{uuid}', library_get)
app.router.add_route('POST', '/api/library', library_add)
app.router.add_route('GET', '/api/playlist', playlist_query)
app.router.add_route('PUT', '/api/playlist/{uuid}', playlist_add)
app.router.add_route('DELETE', '/api/playlist/{uuid}', playlist_remove)
app.router.add_route('GET', '/api/ws', websocket)
app.router.add_static('/', app['config'].get('GENERAL', 'Frontend'))

app.on_shutdown.append(websocket_shutdown)
app.on_shutdown.append(broadcaster_shutdown)

if app['config'].getboolean('GENERAL', 'Debug'):
    logging.basicConfig(level=logging.DEBUG)
else:
    logging.basicConfig(level=logging.WARNING)

app['broadcaster'].start()
web.run_app(app,
            host=app['config'].get('GENERAL', 'Address'),
            port=app['config'].getint('GENERAL', 'Port'))
