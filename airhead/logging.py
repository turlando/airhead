import logging
from pathlib import Path

root = logging.getLogger('airhead')


def init_logging(level, logfile=None):
    try:
        level = getattr(logging, level)
    except AttributeError as e:
        raise Exception("Logging level '{}' is not a legal value."
                        .format(level)) from e
    else:
        logging.basicConfig(level=level)

    if logfile is not None:
        logfile = Path(logfile)
        try:
            logfile.touch()
        except Exception as e:
            raise e
        else:
            logging.basicConfig(filename=logfile)


def get_logger(name):
    return root.getChild(name)
