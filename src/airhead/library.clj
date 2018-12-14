(ns airhead.library
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [airhead.utils :as utils]))

(def ^:private metadata-file-name "metadata.json")

(defn open [path]
  {:path     path
   :metadata (-> (str path "/" metadata-file-name)
                 io/reader
                 (json/read :key-fn keyword))
   :lock     (Object.)})

(defn search
  ([library]
   (-> library :metadata vals))
  ([library query]
   (filter
    #(string/includes?
      (string/lower-case (string/join " " (-> % (dissoc :uuid) vals)))
      (string/lower-case query))
    (->> library :metadata vals))))

(defn get-track [library uuid]
  (-> library :metadata (get (keyword uuid) nil)))
