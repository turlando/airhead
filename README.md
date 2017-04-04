# AirHead

## Running

1. Clone this repo and change your directory to it
2. `cp -p conf/airhead.ini.example conf/airhead.ini`
3. Edit the `PATHS` and `TRASMITTER` sections accordingly
4. Put the output of `openssl rand -hex 32` as `SecretKey` in the `FLASK`
   section.
5. Run web.py
