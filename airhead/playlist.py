import queue
from airhead.library import TrackNotFoundError


class DuplicateTrackError(Exception):
    pass


class Playlist(queue.Queue):
    def __init__(self, library, notify=None, *args, **kwargs):
        self._library = library
        self._notify = notify
        self._current = None
        super(Playlist, self).__init__(*args, **kwargs)

    def _get(self):
        item = self.queue.popleft()
        self._current = item

        if self._notify:
            self._notify()

        return item

    def get(self, *args, **kwargs):
        if not self.qsize():
            self._current = None

        return queue.Queue.get(self, *args, **kwargs)

    def _put(self, item):
        if item not in self.queue and item != self._current:
            # We call this to generate a TrackNotFoundError if a track with
            # such UUID is not present.
            self._library.get_track(item)

            self.queue.append(item)

            if self._notify:
                self._notify()

        else:
            raise DuplicateTrackError

    @property
    def current(self):
        if self._current is not None:
            return self._library.get_track(self._current)

    @property
    def next_(self):
        return [self._library.get_track(uuid)
                for uuid in self.queue]

    def remove(self, item):
        with self.mutex:
            try:
                self.queue.remove(item)
            except ValueError as e:
                raise TrackNotFoundError from e

            if self._notify:
                self._notify()
