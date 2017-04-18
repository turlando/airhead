import queue


class Duplicate(Exception):
    pass


class Playlist(queue.Queue):
    now_playing = None

    def _get(self):
        item = self.queue.popleft()
        self.now_playing = item
        return item

    def _put(self, item):
        if item not in self.queue and item != self.now_playing:
            self.queue.append(item)
        else:
            raise Duplicate

    def dequeue(self, item):
        with self.mutex:
            try:
                self.queue.remove(item)
            except ValueError:
                pass
