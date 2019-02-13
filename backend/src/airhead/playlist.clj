(ns airhead.playlist
  (:require [airhead.utils :as utils]))

(defn- queue [] (clojure.lang.PersistentQueue/EMPTY))

(defn mk-playlist []
  (atom (queue)))

(defn get-queue [playlist]
  (sequence @playlist))

(defn get-playlist [playlist]
  (let [p (sequence @playlist)]
    {:current (first p)
     :next    (rest p)}))

(defn get-current [playlist]
  (first (sequence @playlist)))

(defn get-next [playlist]
  (rest (sequence @playlist)))

(defn push! [playlist x]
  (sequence (swap! playlist conj x)))

(defn pop! [playlist]
  (sequence (swap! playlist pop)))

(defn remove! [playlist x]
  (sequence (swap! playlist #(remove #{x} %))))
