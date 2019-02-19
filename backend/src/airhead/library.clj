(ns airhead.library
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [airhead.utils :as utils])
  (:import (ealvatag.audio AudioFileIO)
           (ealvatag.tag Tag FieldKey)))

(def ^:private metadata-file "metadata.json")

(defn- read-json [^java.io.File path]
  (with-open [r (io/reader path)]
    (json/read r :key-fn (fn [k]
                           (if (some #{"title" "artist" "album" "uuid"} #{k})
                             (keyword k)
                             k)))))

(defn- write-json [m ^java.io.File path]
  (with-open [w (io/writer path)]
    (json/write m w)))

(defn- get-meta-path* [^java.io.File base-path]
  (io/file base-path metadata-file))

(defn- maybe-init-library! [^java.io.File base-path]
  (when (.mkdirs base-path)
    (log/info "Creating new library in " (.getCanonicalPath base-path))
    (spit (get-meta-path* base-path) (json/write-str {}))))

(defn- get-metadata [library]
  (-> library :metadata deref))

(defn- get-track-path [library uuid]
  "Return a path to the track file without checking if it exists in the library
  or in the filesystem."
  (io/file (:base-path library) (str uuid ".ogg")))

(defn- read-codec [^java.io.File path]
  (-> (utils/sh! "ffprobe"
                 "-v" "error"
                 "-select_streams" "a:0"
                 "-show_entries" "stream=codec_name"
                 "-of" "default=nokey=1:noprint_wrappers=1"
                 (.getCanonicalPath path))
      string/trim-newline))

(defn- read-tags [^java.io.File path]
  (let [codec (read-codec path)
        audio (AudioFileIO/readAs path codec)
        tags  ^Tag (.get (.getTag audio))]
    {:title  (.get (.getValue tags FieldKey/TITLE))
     :artist (.get (.getValue tags FieldKey/ARTIST))
     :album  (.get (.getValue tags FieldKey/ALBUM))}))

(defn- transcode! [^java.io.File in ^java.io.File out]
  (utils/sh! "ffmpeg"
             "-i"     (.getCanonicalPath in)
             "-map"   "0:0"
             "-f"     "ogg"
             "-c:a:0" "libvorbis"
             "-q:a:0" "6"
             (.getCanonicalPath out)))

(defn open [^java.io.File base-path]
  (maybe-init-library! base-path)
  (let [meta-path (get-meta-path* base-path)]
    {:base-path base-path
     :meta-path meta-path
     :metadata  (atom (read-json meta-path))
     :lock      (Object.)}))

(defn get-track [library uuid]
  (get (get-metadata library) uuid nil))

(defn get-random-track [library]
  (-> (get-metadata library)
      keys
      rand-nth))

(defn search
  ([library]
   (-> (get-metadata library) vals))
  ([library query]
   (filter
    #(string/includes?
      (string/lower-case (string/join " " (-> % (dissoc :uuid) vals)))
      (string/lower-case query))
    (-> (get-metadata library) vals))))

(defn add [library ^java.io.File file]
  (let [uuid  (utils/uuid)
        tags* (read-tags file)
        tags  (assoc tags* :uuid uuid)
        out   (get-track-path library uuid)
        f     (future
                (transcode! file out)
                (locking (:lock library)
                  (swap! (:metadata library) assoc uuid tags)
                  (write-json (get-metadata library) (:meta-path library)))
                (.delete file))]
    {:tags   tags
     :future f}))

(defn get-track-input-stream
  ^java.io.InputStream
  [library uuid]
  "If the required track exists in the library then return an InputStream or nil
  otherwise."
  (if (get-track library uuid)
    (io/input-stream (get-track-path library uuid))
    nil))

(defn get-atom [library]
  (:metadata library))
