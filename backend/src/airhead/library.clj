(ns airhead.library
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [airhead.utils :as utils])
  (:import (ealvatag.audio AudioFileIO)
           (ealvatag.tag Tag FieldKey)))

(defn- metadata-path [path]
  (str path "/metadata.json"))

(defn open [path]
  (when-not (-> path io/file .exists)
    (throw (Exception. "Could not find library directory.")))
  (let [meta-path (metadata-path path)
        meta-file (io/file meta-path)]
    (when-not (.exists meta-file)
      (spit meta-path (json/write-str {})))
    {:path          path
     :metadata-path meta-path
     :metadata      (atom (-> meta-path
                              io/reader
                              (json/read :key-fn keyword)))
     :lock          (Object.)}))

(defn get-atom [library]
  (:metadata library))

(defn search
  ([library]
   (-> library :metadata deref vals))
  ([library query]
   (filter
    #(string/includes?
      (string/lower-case (string/join " " (-> % (dissoc :uuid) vals)))
      (string/lower-case query))
    (->> library :metadata deref vals))))

(defn get-track [library uuid]
  (-> library :metadata deref (get (keyword uuid) nil)))

(defn get-random-track [library]
  (-> library :metadata deref keys rand-nth name))

(defn get-track-path [library uuid]
  (str (:path library) "/" uuid ".ogg"))

(defn- read-codec [path]
  (-> (utils/sh! "ffprobe"
                 "-v" "error"
                 "-select_streams" "a:0"
                 "-show_entries" "stream=codec_name"
                 "-of" "default=nokey=1:noprint_wrappers=1"
                 path)
      string/trim-newline))

(defn- read-tags [^java.io.File file]
  (let [codec (read-codec (.getPath file))
        audio (AudioFileIO/readAs file codec)
        tags  ^Tag (.get (.getTag audio))]
    {:title  (.get (.getValue tags FieldKey/TITLE))
     :artist (.get (.getValue tags FieldKey/ARTIST))
     :album  (.get (.getValue tags FieldKey/ALBUM))}))

(defn- transcode! [in out]
  (utils/sh! "ffmpeg"
             "-i"     in
             "-map"   "0:0"
             "-f"     "ogg"
             "-c:a:0" "libvorbis"
             "-q:a:0" "6"
             out))

(defn add [library ^java.io.File file]
  (let [uuid  (utils/uuid)
        tags* (read-tags file)
        tags  (assoc tags* :uuid uuid)
        out   (str (:path library) "/" uuid ".ogg")
        f     (future
                (transcode! (.getPath file) out)
                (locking (:lock library)
                  (swap! (:metadata library) assoc (keyword uuid) tags)
                  (spit (:metadata-path library)
                        (json/write-str @(:metadata library)))))]
    {:tags   tags
     :future f}))
