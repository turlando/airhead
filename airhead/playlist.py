import queue
import random
from airhead.library import TrackNotFoundError


class DuplicateTrackError(Exception):
    pass


class EmptyPlaylist(Exception):
    pass


def random_track(library):
    track = random.choice(library.query())
    return track['uuid']


class Playlist:
    def __init__(self, library, notify=lambda: None, auto_dj=False):
        self._library = library
        self._notify = notify
        self._auto_dj = auto_dj

        self._current = None
        self._queue = queue.Queue()

    @property
    def current_track(self):
        if self._current is not None:
            return self._library.get_track(self._current)
        else:
            return None

    @property
    def next_tracks(self):
        return [self._library.get_track(uuid)
                for uuid in self._queue.queue]

    def pop(self):
        try:
            item = self._queue.get(block=False)

        except queue.Empty as e:
            if self._auto_dj:
                track = random_track(self._library)
                self._current = track
                return track
            else:
                self._current = None
                raise EmptyPlaylist from e

        else:
            self._current = item
            return item

        finally:
            self._notify()

    def put(self, item):
        if item not in self._queue.queue and item != self._current:
            if item not in self._library:
                raise TrackNotFoundError

            self._queue.put(item)
            self._notify()

        else:
            raise DuplicateTrackError

    def remove(self, item):
        with self._queue.mutex:
            try:
                self._queue.queue.remove(item)
            except ValueError as e:
                raise TrackNotFoundError from e

            if self._notify:
                self._notify()
