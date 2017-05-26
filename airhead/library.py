from pathlib import Path
from threading import Lock
from uuid import uuid4
from contextlib import contextmanager
import json

from airhead.transcoder import transcode


class TrackNotFoundError(Exception):
    pass


META_FILE = 'metadata.json'
MEDIA_SUFFIX = '.ogg'


class Library:
    def __init__(self, path, notify=lambda: None):
        self._path = Path(path).resolve()
        if not self._path.is_dir():
            raise FileNotFoundError("No such directory:", str(self._path))

        self._meta_path = self._path.joinpath(META_FILE).resolve()
        if not self._meta_path.is_file():
            self._meta_path.touch()

        self._notify = notify
        self._lock = Lock()
        self._meta = {}

        if self._meta_path.stat().st_size:
            with self._meta_path.open() as fp:
                self._meta = json.load(fp)

    @contextmanager
    def update_meta(self):
        self._lock.acquire()

        yield

        with self._meta_path.open(mode='w+') as fp:
            json.dump(self._meta, fp)

        self._notify()
        self._lock.release()

    def get_path(self, uuid):
        return self._path.joinpath(uuid).with_suffix(MEDIA_SUFFIX)

    def get_track(self, uuid):
        try:
            tags = self._meta[uuid]
        except KeyError as e:
            raise TrackNotFoundError from e
        else:
            tags['uuid'] = uuid
            return tags

    def add(self, path, delete=False):
        in_path = Path(path).resolve()
        if not in_path.is_file():
            raise FileNotFoundError("No such file:", str(in_path))

        uuid = str(uuid4())
        out_path = self.get_path(uuid)

        def on_complete(track):
            with self.update_meta():
                self._meta.update(track)

        transcode(in_path, out_path, uuid, on_complete, delete=delete)
        return uuid

    def remove(self, uuid):
        path = self.get_path(uuid)

        with self.update_meta():
            self._meta.pop(uuid)

        try:
            path.unlink()
        except FileNotFoundError:
            pass

    def query(self, q=None):
        if not q:
            return [
                self.get_track(uuid)
                for uuid in self._meta.keys()
            ]
        else:
            return [
                self.get_track(uuid)
                for uuid, tags in self._meta.items()
                if any(q.lower() in tag.lower()
                       for tag in tags.values())
            ]
