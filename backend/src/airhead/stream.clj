(ns airhead.stream
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [airhead.playlist :as playlist]
            [airhead.library :as library]
            [airhead.libshout :as libshout]))

(def ^:private idle-track (io/resource "idle-track.ogg"))

(defn- stream! [ice-conn library playlist random? stop? skip?]
  (log/info "Starting streaming.")
  (try
    (while (not @stop?)
      (if-let [current-track (playlist/get-current playlist)]
        (do
          (log/info "Picking " current-track)
          (with-open [track-stream (library/get-track-input-stream library current-track)]
            (log/info "Starting track streaming.")
            (libshout/send-input-stream! ice-conn track-stream skip?)
            (log/info "Track streaming completed. Skipped:" @skip?)
            (dosync
             (if @skip?
               ;; caller is taking care of dequeing if skip is set
               (ref-set skip? false)
               (playlist/dequeue! playlist)))))
        (do (if (and random? (not (empty? (library/search library))))
              (do
                (log/info "Picking random track.")
                (playlist/enqueue! playlist (library/get-random-track library)))
              (do
                (log/info "No tracks in playlist. Sending idle track.")
                (with-open [idle-stream (io/input-stream idle-track)]
                  (libshout/send-input-stream! ice-conn idle-stream skip?)))))))
    (catch Exception e
      (log/fatal e "Streamer crashed.")))
  (log/info "Streaming stopped.")
  nil)

(defn mk-stream [{:keys [ice-conn library playlist random?]}]
  (let [stop? (ref false :validator boolean?)
        skip? (ref false :validator boolean?)]
    {:stop-ref stop?
     :skip-ref skip?
     :future   (future (stream! ice-conn library playlist random? stop? skip?))}))

(defn stop! [{:keys [skip-ref stop-ref]}]
  (log/info "Stopping streaming.")
  (dosync
   (ref-set skip-ref true)
   (ref-set stop-ref true))
  nil)

(defn skip! [{:keys [skip-ref]}]
  (log/info "Skipping track.")
  (dosync
   (ref-set skip-ref true)))
