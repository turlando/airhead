(ns airhead.utils
  (:require [clojure.string :as string]
            [clojure.java.io :as io]))

(def not-nil? (complement nil?))
(def not-blank? (complement string/blank?))

(defn read-int [s]
  (Integer/parseInt (re-find #"^-?\d+$" s)))

(defn read-float [s]
  (Float/parseFloat (re-find #"^-?\d+\.\d+$" s)))

(defn map-vals [f m]
  (zipmap (keys m)
          (map f (vals m))))

(defn some-vals [m]
  (into {} (remove (comp nil? second) m)))

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

(defn slurp-resource [path]
  (-> path
      io/resource
      slurp))

(defn ^java.io.Reader resource-reader [path]
  (-> path
      io/resource
      io/reader))

(defn find-file-in-dirs [file dirs]
  "Returns the first path where file is any of dirs or nil otherwise."
  (let [paths (map #(str % file) dirs)]
    (seek #(.exists (^java.io.File io/file %)) paths)))
