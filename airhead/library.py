from pathlib import Path
from threading import Lock
from uuid import uuid4
import json

from airhead.transcoder import Transcoder


META_FILE = 'metadata.json'


class Library:
    def __init__(self, path, notify=None):
        self._path = Path(path).resolve()
        if not self._path.is_dir():
            raise FileNotFoundError("No such directory:", str(self._path))

        self._meta_path = self._path.joinpath(META_FILE).resolve()
        if not self._meta_path.is_file():
            self._meta_path.touch()

        self._notify = notify
        self._transcoder = Transcoder(self)

        self._lock = Lock()
        self._meta = {}

        if self._meta_path.stat().st_size:
            self._load()

    def _load(self):
        "Loads tags to self._meta from metadata.json."
        with self._meta_path.open() as fp:
            self._meta = json.load(fp)

    def _save(self):
        "Flushes updates from self._meta to metadata.json."
        with self._meta_path.open(mode='w+') as fp:
            json.dump(self._meta, fp)

    def _add(self, d):
        self._lock.acquire()

        self._meta.update(d)
        self._save()

        if self._notify:
            self._notify()

        self._lock.release()

    def _remove(self, uuid):
        self._lock.acquire()

        self._meta.pop(uuid)
        self._save()

        if self._notify:
            self._notify()

        self._lock.release()

    def get_path(self, uuid):
        return self._path.joinpath(uuid).with_suffix('.ogg')

    def get_tags(self, uuid):
        return self._meta[uuid]

    def add(self, path, delete=False):
        path = Path(path).resolve()
        if not path.is_file():
            raise FileNotFoundError("No such file:", str(path))

        uuid = str(uuid4())
        self._transcoder.process(path, uuid, delete=delete)
        return uuid

    def remove(self, uuid):
        self._remove(uuid)

        path = self.get_path(uuid)
        try:
            path.unlink()
        except FileNotFoundError:
            pass

    def query(self, q=None):
        if not q:
            return list(self._meta.keys())
        else:
            return [
                uuid
                for uuid, tags in self._meta.items()
                if any(q.lower() in tag.lower()
                       for tag in tags.values())
            ]
