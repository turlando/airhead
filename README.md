# AirHead

AirHead is a simple REST application that allows you to run a webradio
where users can upload tracks (Ogg Vorbis, MP3 and FLAC) in order to
build a shared collective library. The user can then enqueue the tracks that
will be streamed out to an Icecast server.

## Deploy

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

We will also assume that nginx will run as the `www` user.

### REST API

1. Give yourself root privileges with either `su -` or `sudo su -`

2. Clone this repo in `/opt/airhead` with

       git clone --depth=1 https://github.com/turlando/airhead.git /opt/airhead

3. Copy and edit the configuration

       mkdir /etc/airhead
       cp -p /opt/airhead/conf/airhead.ini.example /etc/airhead/airhead.ini

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

### Web frontend

1. Clone the `airhead-cljs` repo somewhere

       git clone https://github.com/edne/airhead-cljs.git /tmp/airhead-cljs

2. Compile it

       cd /tmp/airhead-cljs
       lein release

3. Move it to the public httpd path

       mkdir /var/www/airhead
       cp -pr /tmp/airhead-cljs/output/* /var/www/airhead
       chown -R www:www /var/www/airhead

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
        }

        location  /  {
            root /var/www/airhead
            index index.html;
        }
    }
