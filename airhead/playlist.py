import queue
import random
from airhead.library import TrackNotFoundError


class DuplicateTrackError(Exception):
    pass


class EmptyPlaylist(Exception):
    pass


class Playlist:
    def __init__(self, library, notify=lambda: None, auto_dj=False):
        self._library = library
        self._notify = notify
        self._auto_dj = auto_dj

        self._current = None
        self._queue = queue.Queue()
        self._played = set()

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

    def _choose_track(self):
        all_tracks = {track['uuid'] for track in self._library.query()}

        if self._played == all_tracks:
            self._played = set()

        return random.choice(list(all_tracks - self._played))

    def _play_track(self, uuid):
        self._current = uuid
        self._played.add(uuid)

    def pop(self):
        try:
            uuid = self._queue.get(block=False)

        except queue.Empty as e:
            # if auto-dj is enabled and the library is not empty
            if self._auto_dj and self._library.query():
                uuid = self._choose_track()
                self._play_track(uuid)
                return uuid
            else:
                self._current = None
                raise EmptyPlaylist from e

        else:
            self._play_track(uuid)
            return uuid

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
