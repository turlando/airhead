(ns airhead.stream
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [airhead.playlist :as p]
            [airhead.library :as l]
            [airhead.libshout :as libshout]))

(defn- stream! [ice-conn library playlist stop? skip?]
  (log/info "Starting streaming.")
  (while (not @stop?)
    (if-let [current-track (-> (p/status playlist) first)]
      (do
        (log/info "Picking " current-track)
        (let [track-path (l/get-track-path library current-track)]
          (with-open [track-stream (-> track-path io/input-stream)]
            (log/info "Starting track streaming.")
            (libshout/send-input-stream! ice-conn track-stream skip?)
            (p/pop! playlist)
            (log/info "Track streaming completed."))))
      (do (log/info "No tracks in playlist. Sleeping.")
          (Thread/sleep 1000))))
  (log/info "Streaming completed."))

(defn mk-stream [{:keys [ice-conn library playlist]}]
  (let [stop? (ref false :validator boolean?)
        skip? (ref false :validator boolean?)]
    {:stop-ref stop?
     :skip-ref skip?
     :future   (future (stream! ice-conn library playlist stop? skip?))}))

(defn stop! [{:keys [skip-ref stop-ref]}]
  (log/info "Stopping streaming.")
  (dosync
   (ref-set skip-ref true)
   (ref-set stop-ref true))
  nil)

(defn skip! [s]
  (log/info "Skipping track.")
  (dosync
   (ref-set (:skip-ref s) true)))
