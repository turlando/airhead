(ns airhead.utils
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]))

(def not-nil? (complement nil?))
(def not-blank? (complement string/blank?))

(defn seek
  "Returns first item from coll for which (pred item) returns true.
   Returns nil if no such item is present, or the not-found value if supplied."
  ([pred coll] (seek pred coll nil))
  ([pred coll not-found]
   (reduce (fn [_ x]
             (if (pred x)
               (reduced x)
               not-found))
           not-found coll)))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn find-file-in-dirs
  "Returns the first path where file is any of dirs or nil otherwise."
  [file dirs]
  (let [paths (map #(str % file) dirs)]
    (seek #(.exists (io/file %)) paths)))

(defn sh! [cmd & args]
  (let [command               (conj args cmd)
        ret                   (apply shell/sh command)
        {:keys [^Integer exit
                ^String out
                ^String err]} ret]
    (when-not (zero? exit)
      (throw (Exception. err)))
    out))
