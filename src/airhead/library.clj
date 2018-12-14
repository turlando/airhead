(ns airhead.library
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [airhead.utils :as utils])
  (:import (ealvatag.audio AudioFileIO)
           (ealvatag.tag FieldKey)))

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

(defn- read-tags [file]
  (let [audio (AudioFileIO/read file)
        tags  (-> (.getTag audio) .get)]
    {:title  (-> (.getValue tags FieldKey/TITLE) .get)
     :artist (-> (.getValue tags FieldKey/ARTIST) .get)
     :album  (-> (.getValue tags FieldKey/ALBUM) .get)}))

(defn- transcode! [in out]
  (utils/ffmpeg! "-i"     in
                 "-map"   "0:0"
                 "-f"     "ogg"
                 "-c:a:0" "libvorbis"
                 "-q:a:0" "6"
                 out))

(defn add [library file]
  (let [uuid  (utils/uuid)
        tags* (read-tags file)
        tags  (assoc tags* :uuid uuid)
        out   (str (:path library) "/" uuid ".ogg")]
    (future
      (transcode! (.getPath file) out)
      (locking (:lock library)
        (swap! (:metadata library) assoc (keyword uuid) tags)
        (spit (:metadata-path library)
              (json/write-str @(:metadata library)))))))
