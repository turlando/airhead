# AirHead

AirHead is a simple REST application that allows you to run a webradio
where users can upload tracks (Ogg Vorbis, MP3 and FLAC) in order to
build a shared collective library. The user can then enqueue the tracks that
will be streamed out to an Icecast server.

## Deploy

A complete deploy includes both REST APIs (this repo) and a frontend. As of now
the only one available is
[airhead-frontend](https://github.com/edne/airhead-frontend) by
[edne](https://github.com/edne).
This document will walk you trough everything needed for an usable installation.

Before you start make sure you can satisfy the following dependencies:

 * FFmpeg
 * Icecast 2 (not necessarily on the same host) and libshout2
 * Python 3.5 or better (Python 2 is **not** supported)
 * python-virtualenv (suggested, not required)
 * `clojurescript` and `lein` (for the frontend)
 * Any reverse proxy (nginx is suggested)

We will assume the following paths:

 * REST API: `/opt/airhead`
 * REST API configuration: `/etc/airhead`
 * Web frontend: `/var/www/airhead`

We will also assume that nginx will run as the `www` user and AirHead will run
as a new Unix account you should have already created named `airhead`.

Some commands requires root privileges and are marked by the use of `sudo`. If
`sudo` is not available in your system remember that you need to run the said
command as root.

### REST API

1. Create the AirHead directory with proper permissions.

       sudo mkdir /opt/airhead
       sudo chown airhead /opt/airhead

2. Switch to the airhead account with `su - airhead` or `sudo su - airhead` and
   clone this repo in `/opt/airhead` with

       git clone --depth=1 https://github.com/turlando/airhead.git /opt/airhead

3. Copy and edit the configuration

       sudo mkdir /etc/airhead
       sudo cp -p /opt/airhead/conf/airhead.ini.example /etc/airhead/airhead.ini

   Edit `/etc/airhead/airhead.ini` with any text editor.
   **Remember to always use absolute paths.**

   1. In the `[GENERAL]` section change `Library` to the music library.
      If you're testing the software and you want to have the frontend served
      at URL `/` change also `Frontend` to its path. If you want to change the
      socket AirHead will bind to set the `Address` and `Port` variables.
   2. In the `[ICECAST]` section change `Host`, `Port`, `Username` and
      `password` to reflect your Icecast configuration. `Mount` will be the
      used Icecast mount and does not require a leading slash.
   3. In the `[FFMPEG]` section change `Exe` to point to your `ffmpeg`
      binary.

4. Create a virtualenv and install pip dependencies

   If you want to run AirHead inside a virtualenv run:

       cd /opt/airhead
       virtualenv -p python3 env
       source env/bin/activate # for bash
       . env/bin/activate # for sh

   Then install pip dependencies with:
       pip install -r requirements.txt

    Run the software with:
        python web.py

Note: as of now there is no way to keep the software running in a daemonized
way. The ugliest and speedy way is to run it inside a screen/tmux session. We
suggest not to do it. The best way would be to run it using your init system.
Currently we don't provide init script, but once the software is stable we will
definitely do.

### Web frontend

1. Clone the `airhead-frontend` repo somewhere

       git clone https://github.com/edne/airhead-frontend.git /tmp/airhead-frontend

2. Compile it

       cd /tmp/airhead-frontend
       lein release

3. Move it to the public httpd path

       sudo mkdir /var/www/airhead
       sudo cp -pr /tmp/airhead-frontend/output/* /var/www/airhead
       sudo chown -R www:www /var/www/airhead

### Configure nginx

You can test this software without the needing of any HTTP proxy by just
making the `Frontend` variable in the `[GENERAL]` condifiguration section point
to the frontend path and running `web.py`.

If you want a more stable deploy you can make nginx host the frontend and
reverse the API calls.

Adjust your server section as follows:

    server {
        location ~ ^/api {
            proxy_pass http://127.0.0.1:8080;
            proxy_set_header Host $http_host;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_redirect off;
            proxy_buffering off;

            # WebSocket settings
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_read_timeout 1h;
        }

        location  /  {
            root /var/www/airhead
            index index.html;
        }
    }

## REST APIs specification

### Get system info

You can edit `name` and `greet_message` in the `[INFO]` section of the
configuration file. `stream_url` reflects the `[ICECAST]` section.

* **URL**: `/api/info`
* **Method**: `GET`
* **Success response**:
  * **Code**: 200
  * **Content**:
    ```
    {
        "name": "AirHead",
        "greet_message": "Welcome!",
        "stream_url": "http://127.0.0.1:8000/airhead"
    }
* **Sample call**:
  `curl "http://127.0.0.1:8080/api/info"`

### Get tracks in the library

This method allows to get all tracks or to search trough the library.

* **URL**: `/api/library`
* **Method**: `GET`
* **URL params**:
  * (**Optional**) `q=[alphanumeric]`
    Filter tracks containing the query string in their tags.
* **Success response**:
  * **Code**: 200
  * **Content**:
    ```
    {
        "tracks": [
            {
                "title": "Notte in Bovisa",
                "artist": "Calibro 35",
                "album": "Calibro 35",
                "uuid": "b3b5099b-63fd-4322-92b8-0346c305f1e6
            },
            {
                "title": "Drum & Bass Wise (remix)",
                "artist": "Remarc",
                "album": "Sound Murderer",
                "uuid": "3b69ba1a-47ad-4faf-896a-e17eb5026597"
            },
            {
                "title": "Nothing at All",
                "artist": "Gentle Giant",
                "album": "Gentle Giant",
                "uuid": "c9bc6e46-3078-45fd-a7e1-0d5c21ce2d59"
            }
        ]
    }
    ```
* **Sample call**:
  `curl "http://127.0.0.1:8080/api/library"`
* **Notes**:
  If no tracks are found it will return an empty list.

### Get information about a track

* **URL**: `/api/library/{uuid}`
* **Method**: `GET`
* **Success response**:
  * **Code**: 200
  * **Content**:
    ```
    {
        "title": "Drum & Bass Wise (remix)",
        "artist": "Remarc",
        "album": "Sound Murderer",
        "uuid": "3b69ba1a-47ad-4faf-896a-e17eb5026597"
    }
    ```
* **Error response**:
  * ***Track not found***
    * **Code**: 400
    * **Content**:
      ```
      {
          "err": "uuid_not_valid",
          "msg": "No track found with such UUID."
      }
      ```
* **Sample call**:
  `curl "http://127.0.0.1:8080/api/library/3b69ba1a-47ad-4faf-896a-e17eb5026597"`

### Upload a track to the library

* **URL**: `/api/library`
* **Method**: `POST`
* **Success response**:
  * **Code**: 200
  * **Content**:
    ```
    {
        "track": "b3b5099b-63fd-4322-92b8-0346c305f1e6"
    }
    ```
* **Error response**:
  * ***Upload failed***
    * **Code**: 500
    * **Content**: 
      ```
      {
          "err": "upload_failed",
          "msg": "This is strange."
      }
      ```
  * ***File type not supported***
    * **Code**: 400
    * **Content**: 
      ```
      {
          "err": "illegal_codec",
          "msg": "This kind of file is not supported."
      }
      ```
* **Sample call**:
  ```
  curl -i -X POST -H "Content-Type: multipart/form-data" \
  -F "track=@MFunk/Calibro 35/2008 - Calibro 35/04. Notte in Bovisa.ogg" \
  http://127.0.0.1:8080/api/library
  ```

### Get playlist status
* **URL**: `/api/playlist`
* **Method**: `GET`
* **Success response**:
  * **Code**: 200
  * **Content**:
    ```
    {
        "current": {
            "title": "Notte in Bovisa",
            "artist": "Calibro 35",
            "album": "Calibro 35",
            "uuid": "b3b5099b-63fd-4322-92b8-0346c305f1e6"
        },
        "next": [
            {
                "title": "Drum & Bass Wise (remix)",
                "artist": "Remarc",
                "album": "Sound Murderer",
                "uuid": "3b69ba1a-47ad-4faf-896a-e17eb5026597"
            },
            {
                "title": "Nothing at All",
                "artist": "Gentle Giant",
                "album": "Gentle Giant",
                "uuid": "c9bc6e46-3078-45fd-a7e1-0d5c21ce2d59"
            }
        ]
    }
    ```
* **Sample call**:
  `curl "http://127.0.0.1:8080/api/playlist"`
* **Notes**:
  `current` is `none` if no tracks is playing. `next` is an empty list if there
  are no tracks next.

### Add a track to the playlist

* **URL**: `/api/playlist/{uuid}`
* **Method**: `PUT`
* **Success response**:
  * **Code**: 200
  * **Content**: `{}`
* **Error response**:
  * ***Invalid uuid. There is no track with such uuid in the library.***
    * **Code**: 400
    * **Content**:
      ```
      {
          "err": "track_not_found",
          "msg": "No track found with such UUID."
      }
      ```
  * ***Track already present in the playlist***
    * **Code**: 400
    * **Content**:
      ```
      {
          "err": "duplicate",
          "msg": "The track is already present in the playlist."
      }
      ```
* **Sample call**:
  `curl -X PUT "http://127.0.0.1:8080/api/playlist/c9bc6e46-3078-45fd-a7e1-0d5c21ce2d59"`

### Remove a track from the playlist

* **URL**: `/api/playlist/{uuid}`
* **Method**: `DELETE`
* **Success response**:
  * **Code**: 200
  * **Content**: `{}`
* **Error response**:
  * ***Invalid uuid. There is no track with such uuid in the playlist.***
    * **Code**: 400
    * **Content**:
      ```
      {
          "err": "track_not_found",
          "msg": "No track found with such UUID."
      }
      ```
* **Sample call**:
  `curl -X DELETE "http://127.0.0.1:8080/api/playlist/c9bc6e46-3078-45fd-a7e1-0d5c21ce2d59"`
