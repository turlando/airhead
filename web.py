import os.path
from uuid import uuid4
import json
import atexit

from queue import Queue

from flask import Flask, request, redirect, render_template
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


@app.route('/')
def home():
    msg = conf_flask['GreetMessage']
    stream_url = "http://{}:{}/{}".format(
        conf_transmitter['Host'],
        conf_transmitter['Port'],
        conf_transmitter['Mount'])
    return render_template('home.html', greet_msg=msg, stream_url=stream_url)


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


def get_tracks(path):
    tracks = []

    try:
        for f in os.listdir(path):
            if f.endswith(".json"):
                with open(os.path.join(path, f)) as fp:
                    track = json.load(fp)
                    track['uuid'] = os.path.splitext(f)[0]
                    tracks.append(track)

    except FileNotFoundError:
        pass

    return tracks


@app.route('/tracks')
def tracks():
    path = conf_paths['Tracks']
    tracks = get_tracks(path)
    return render_template('tracks.html', tracks=tracks)


def get_queue(queue):
    tracks = []
    for uuid in queue:
        with open(os.path.join(conf_paths['Tracks'], uuid + '.json')) as fp:
            tracks.append(json.load(fp))
    return tracks


@app.route('/queue')
def queue():
    tracks = get_queue(transmitter_queue.queue)
    return render_template('queue.html', queue=tracks)


@app.route('/enqueue/<uuid:uuid>', methods=['POST'])
def enqueue(uuid):
    transmitter_queue.put(str(uuid))
    return '', 200


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080, debug=False)
