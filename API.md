# AirHead API

## Retrieve basic information

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
    ```
* **Sample call**:
  `curl "http://127.0.0.1:8080/api/info"`
* **Notes**:
  You can edit `name` and `greet_message` in the `:info` map of the
  configuration file. `stream_url` reflects the `:icecast` map.
  
## Library operations

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
                "uuid": "b3b5099b-63fd-4322-92b8-0346c305f1e6"
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
  
## Playlist operations

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
* **Notes**:
  If called with the uuid of the current playing track it will be skipped
