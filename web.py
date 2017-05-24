import aiohttp
from aiohttp import web

import logging

from tempfile import NamedTemporaryFile as TemporaryFile

from airhead.config import get_config
from airhead.library import Library
from airhead.playlist import Playlist, DuplicateError
from airhead.broadcaster import Broadcaster


logger = logging.getLogger(__name__)


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
        q = None

    tracks = app['library'].query(q)
    return web.json_response({
        'status': 'success',
        'tracks': tracks
    })


async def library_get(request):
    uuid = request.match_info['uuid']
    return web.json_response({
        'status': 'success',
        'track': app['library'].get_tags(uuid)
    })


async def library_add(request):
    reader = await request.multipart()
    path = await store_file(reader)

    uuid = app['library'].add(path, delete=True)
    return web.json_response({
        'status': 'success',
        'uuid': uuid
    })


async def playlist_query(request):
    return web.json_response({
        'status': 'success',
        'current': app['playlist'].current,
        'next': app['playlist'].next_
    })


async def playlist_add(request):
    logger.debug("Adding to playlist")
    uuid = request.match_info['uuid']
    try:
        app['playlist'].put(uuid)
    except DuplicateError:
        return web.json_response({'status': 'duplicate'})
    finally:
        return web.json_response({'status': 'success'})


async def playlist_remove(request):
    uuid = request.match_info['uuid']
    try:
        app['playlist'].remove(uuid)
    except KeyError:
        return web.json_response({'status': 'error'})
    finally:
        return web.json_response({'status': 'success'})


async def websocket(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    request.app['websockets'].add(ws)

    async for msg in ws:
        pass

    await websocket_shutdown(app, ws)

    return ws


async def websocket_shutdown(app, ws=None):
    if ws:
        ws.close(code=aiohttp.WSCloseCode.GOING_AWAY)
    else:
        for client in app['websockets']:
            await client.close(code=aiohttp.WSCloseCode.GOING_AWAY)


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
app['library'] = Library(app['config'].get('GENERAL', 'Library'),
                         notify=broadcast_library_update)
app['playlist'] = Playlist(app['library'], notify=broadcast_playlist_update)
app['broadcaster'] = Broadcaster(app['config']['ICECAST'], app['playlist'])
app['websockets'] = set()

app.router.add_route('GET', '/api/info', info)
app.router.add_route('GET', '/api/library', library_query)
app.router.add_route('GET', '/api/library/{uuid}', library_get)
app.router.add_route('POST', '/api/library', library_add)
app.router.add_route('GET', '/api/playlist', playlist_query)
app.router.add_route('PUT', '/api/playlist/{uuid}', playlist_add)
app.router.add_route('DELETE', '/api/playlist/{uuid}', playlist_remove)
app.router.add_route('GET', '/ws', websocket)
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
