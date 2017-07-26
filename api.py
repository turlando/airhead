import asyncio
from aiohttp import web

from airhead.config import get_config
from airhead.logging import setup_logging
from airhead.library import Library
from airhead.playlist import Playlist
from airhead.broadcaster import Broadcaster
from airhead.routes import setup_routes
from airhead.websocket import websocket_shutdown, \
                              broadcast_library_update, \
                              broadcast_playlist_update


def setup_app(loop, config):
    app = web.Application(loop=loop)

    app['config'] = config
    app['websockets'] = list()

    app['library'] = Library(config.get('GENERAL', 'Library'),
                             notify=lambda: broadcast_library_update(app))
    app['playlist'] = Playlist(app['library'],
                               notify=lambda: broadcast_playlist_update(app),
                               auto_dj=config.getboolean('GENERAL', 'AutoDJ'))
    app['broadcaster'] = Broadcaster(config['ICECAST'], app['playlist'])

    app.on_startup.append(lambda app: app['broadcaster'].start())

    app.on_shutdown.append(websocket_shutdown)
    app.on_shutdown.append(lambda app: app['broadcaster'].join())

    setup_routes(app)

    return app


if __name__ == '__main__':
    config = get_config()

    setup_logging(config.get('LOGGING', 'Level'),
                  config.get('LOGGING', 'Path'))

    loop = asyncio.get_event_loop()

    app = setup_app(loop, config)
    web.run_app(app,
                host=config.get('GENERAL', 'Address'),
                port=config.getint('GENERAL', 'Port'))
