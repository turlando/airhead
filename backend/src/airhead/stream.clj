(ns airhead.stream
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [airhead.playlist :as playlist]
            [airhead.library :as library]
            [airhead.libshout :as libshout]))


(def ^:private idle-track (io/resource "idle-track.ogg"))

(defn- stream! [ice-conn library playlist stop? skip?]
  (log/info "Starting streaming.")
  (while (not @stop?)
    (if-let [current-track (-> (playlist/status playlist) first)]
      (do
        (log/info "Picking " current-track)
        (let [track-path (library/get-track-path library current-track)]
          (with-open [track-stream (-> track-path io/input-stream)]
            (log/info "Starting track streaming.")
            (libshout/send-input-stream! ice-conn track-stream skip?)
            (log/info "Track streaming completed. Skipped:" @skip?)
            (dosync (ref-set skip? false))
            (playlist/pop! playlist))))
      (do (log/info "No tracks in playlist. Sending idle track.")
          (with-open [idle-stream (io/input-stream idle-track)]
            (libshout/send-input-stream! ice-conn idle-stream skip?)))))
  (log/info "Streaming completed.")
  nil)

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

(defn skip! [{:keys [skip-ref]}]
  (log/info "Skipping track.")
  (dosync
   (ref-set skip-ref true)))