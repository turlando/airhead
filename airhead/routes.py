from airhead.logging import get_logger
from airhead.rest import info
from airhead.rest import library_query, library_get, library_add
from airhead.rest import playlist_query, playlist_add, playlist_remove, \
                         playlist_skip
from airhead.websocket import websocket


def setup_routes(app):
    app.router.add_route('GET', '/api/info', info)
    app.router.add_route('GET', '/api/library', library_query)
    app.router.add_route('GET', '/api/library/{uuid}', library_get)
    app.router.add_route('POST', '/api/library', library_add)
    app.router.add_route('GET', '/api/playlist', playlist_query)
    app.router.add_route('PUT', '/api/playlist/{uuid}', playlist_add)
    app.router.add_route('DELETE', '/api/playlist/{uuid}', playlist_remove)
    app.router.add_route('GET', '/api/playlist/skip/{uuid}', playlist_skip)
    app.router.add_route('GET', '/api/ws', websocket)
    app.router.add_static('/', app['config'].get('GENERAL', 'Frontend'))
