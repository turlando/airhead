import queue


class DuplicateError(Exception):
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
            self.queue.append(item)

            if self._notify:
                self._notify()

        else:
            raise DuplicateError

    def query(self):
        current = self._library.get_tags(self._current) \
                  if self._current else None
        next_ = [self._library.get_tags(track)
                 for track in self.queue] \
                if not self.empty() else []

        return {
            'current': current,
            'next': next_
        }

    def remove(self, item):
        with self.mutex:
            self.queue.remove(item)

            if self._notify:
                self._notify()
