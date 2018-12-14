(ns airhead.library
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [airhead.utils :as utils]))

(def ^:private metadata-file-name "metadata.json")

(defn open-library [path]
  {:path     path
   :metadata (-> (str path "/" metadata-file-name)
                 io/reader
                 (json/read :key-fn keyword))
   :lock     (Object.)})

(defn search-library
  ([library]
     (-> library :metadata vals))
  ([library pattern]
   (filter
    #(re-matches pattern (string/join " " (-> % (dissoc :uuid) vals)))
    (->> library :metadata vals))))
