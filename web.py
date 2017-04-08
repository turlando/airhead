import os.path
from uuid import uuid4
import json
import atexit

from queue import Queue

from flask import Flask, request, redirect, jsonify, render_template
from flask_wtf import FlaskForm
from flask_wtf.csrf import CSRFProtect
from flask_wtf.file import FileField, FileRequired
from wtforms.validators import StopValidation

import mutagen
from mutagen.oggvorbis import OggVorbis
from mutagen.mp3 import MP3

from airhead.config import get_config
from airhead.transmitter import Transmitter
from airhead.transcoder import Transcoder


conf = get_config()
conf_paths = conf['PATHS']
conf_flask = conf['FLASK']
conf_transmitter = conf['TRANSMITTER']

transmitter_queue = Queue()
transmitter = Transmitter(conf, transmitter_queue)
transmitter.start()
atexit.register(transmitter.join)

transcoder_queue = Queue()
transcoder = Transcoder(conf, transcoder_queue)
transcoder.start()
atexit.register(transcoder.join)

app = Flask(__name__)
csrf = CSRFProtect(app)

app.config['SECRET_KEY'] = conf_flask['SecretKey']

app.jinja_env.trim_blocks = True
app.jinja_env.lstrip_blocks = True


class AudioFileRequired:
    """
    Validates that an uploaded file from a flask_wtf FileFiled is actually
    an audio file (Vorbis or MP3 for the moment) using mutagen to check
    the headers.
    """

    field_flags = ('required', )

    DEFAULT_MESSAGE = "Invalid audio file."

    def __init__(self, message=None):
        self.message = (message if message
                        else self.DEFAULT_MESSAGE)

    def __call__(self, form, field):
        s = field.data.stream
        f = mutagen.File(s)

        if not (isinstance(f, OggVorbis)
                or isinstance(f, MP3)):
            raise StopValidation(self.message)

        s.seek(0)


class UploadForm(FlaskForm):
    track = FileField(validators=[FileRequired(), AudioFileRequired()])


def get_tags(uuid):
    path = os.path.join(conf_paths['Tracks'], uuid + '.json')
    with open(path) as fp:
        track = json.load(fp)
        track['uuid'] = uuid
        return track


def grep_tags(path, query):
    with open(path) as fp:
        track = json.load(fp)

        return any((query.lower() in value.lower()
                    for value in track.values()))


def get_tracks(query=None):
    tracks = []
    base = os.path.join(conf_paths['Tracks'])

    try:
        for f in os.listdir(base):
            if f.endswith('.json'):

                path = os.path.join(base, f)
                uuid = os.path.splitext(f)[0]

                if query:
                    if grep_tags(path, query):
                        tracks.append(uuid)

                else:
                    tracks.append(uuid)

    except FileNotFoundError:
        pass

    return tracks


def paginate(tracks, limit=10, page=0):
    start = limit * page
    end = start + limit

    try:
        return tracks[start:end]
    except IndexError:
        return tracks[start:]


@app.route('/api/tracks', methods=['GET'])
def tracks():
    limit = int(request.args.get('limit', '10'))
    page = int(request.args.get('page', '0'))
    query = request.args.get('q', None)

    tracks = [get_tags(uuid)
              for uuid in get_tracks(query=query)]

    return jsonify(total=len(tracks),
                   items=paginate(tracks, limit=limit, page=page)), 200


@app.route('/api/queue', methods=['GET'])
def queue():
    limit = int(request.args.get('limit', '10'))
    page = int(request.args.get('page', '0'))

    tracks = [get_tags(uuid)
              for uuid in transmitter_queue.queue]

    return jsonify(total=len(tracks),
                   items=paginate(tracks, limit=limit, page=page)), 200


@app.route('/api/enqueue/<uuid:uuid>', methods=['PUT'])
def enqueue(uuid):
    transmitter_queue.put(str(uuid))
    return '', 200


@app.route('/tracks')
def tracks_():
    tracks = [get_tags(uuid)
              for uuid in get_tracks()]
    return render_template('tracks.html', tracks=tracks)


@app.route('/upload', methods=['GET', 'POST'])
def upload():
    form = UploadForm()

    if request.method == 'POST' and form.validate_on_submit():
        uuid = str(uuid4())
        path = os.path.join(conf_paths['Upload'], uuid)
        f = form.track.data

        f.save(path)
        transcoder_queue.put(uuid)
        return redirect('/')

    return render_template('upload.html', form=form)


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080, debug=False)
