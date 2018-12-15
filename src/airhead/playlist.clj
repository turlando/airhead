(ns airhead.playlist
  (:require [airhead.utils :as utils]))

(defn make-playlist []
  (atom (utils/queue)))

(defn status [playlist]
  (sequence @playlist))

(defn push! [playlist x]
  (sequence (swap! playlist conj x)))

(defn pop! [playlist]
  (sequence (swap! playlist pop)))

(defn remove! [playlist x]
  (sequence (swap! playlist #(remove #{x} %))))
