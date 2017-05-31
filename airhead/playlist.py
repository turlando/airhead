import queue
from airhead.library import TrackNotFoundError


class DuplicateTrackError(Exception):
    pass


class Playlist:
    def __init__(self, library, notify=lambda: None):
        self._library = library
        self._notify = notify
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

    def get(self):
        if self._queue.empty():
            self._current = None

        item = self._queue.get(block=False)
        self._current = item
        self._notify()

        return item

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
