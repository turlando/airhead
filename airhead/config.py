import os.path
from configparser import ConfigParser


CONFIG_PATHS = ['.', 'conf/', '~/.config/airhead',
                '/usr/local/etc/airhead', '/etc/airhead']


def get_config():
    for p in CONFIG_PATHS:
        path = os.path.join(p, 'airhead.ini')
        if os.path.isfile(path):
            c = ConfigParser()
            c.read(path)
            return c
    else:
        raise Exception("Config file 'airhead.ini' not found in any of {}."
                        .format(', '.join(CONFIG_PATHS)))


idle_media = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    'media', 'idle.ogg')
