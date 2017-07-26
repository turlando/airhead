from aiohttp.web import WebSocketResponse
from airhead.logging import get_logger

log = get_logger('websocket')


async def websocket(request):
    ws = WebSocketResponse()
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


async def websocket_shutdown(app):
    log.debug("Disconnecting {} clients.".format(len(app['websockets'])))
    for client in app['websockets']:
        await client.close()


def broadcast_library_update(app):
    for client in app['websockets']:
        client.send_json({'update': 'library'})


def broadcast_playlist_update(app):
    for client in app['websockets']:
        client.send_json({'update': 'playlist'})
