import logging
from pathlib import Path

root = logging.getLogger('airhead')


def _setup_log_level(level):
    try:
        level = getattr(logging, level)
    except AttributeError as e:
        raise Exception("Logging level '{}' is not a legal value."
                        .format(level)) from e
    else:
        logging.basicConfig(level=level)


def _setup_log_file(file_):
    if file_ is not None:
        logging.basicConfig(filename=file_)


def setup_logging(level, logfile=None):
    _setup_log_level(level)
    _setup_log_file(logfile)


def get_logger(name):
    return root.getChild(name)
