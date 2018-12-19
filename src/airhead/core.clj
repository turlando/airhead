(ns airhead.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.edn :as edn]
            [airhead.utils :as utils]
            [airhead.library :as library]
            [airhead.playlist :as playlist]
            [airhead.libshout :as libshout]
            [airhead.server :as server]
            [airhead.stream :as stream]))

(def ^:private config-file "airhead.edn")
(def ^:private config-dirs ["./"
                            "/etc/airhead/"
                            "/usr/local/etc/airhead/"])

(defn stop!
  [{:keys [ice-conn stream]
    :as   args}]
  (log/info "Stopping Airhead")
  (server/stop! args)
  (stream/stop! stream)
  (try
    (libshout/close! ice-conn)
    (catch Exception e
      (log/error "Error while closing Icecast connection: "
                 (:cause (Throwable->map e))))
    (finally
      (libshout/deinit-lib!)))
  nil)

(defn start! []
  (log/info "Starting Airhead.")
  (if-let [config-path (utils/find-file-in-dirs config-file config-dirs)]
    (do (log/info "Reading configuration from " config-path)
        (let [config      (edn/read-string (slurp config-path))
              lib-path    (-> config :library :path)
              library     (library/open lib-path)
              playlist    (playlist/mk-playlist)
              ice-conn    (do (libshout/init-lib!)
                              (libshout/open (:icecast config)))
              stream      (stream/mk-stream {:ice-conn ice-conn
                                             :library  library
                                             :playlist playlist})
              http-server (server/start! {:config   config
                                          :library  library
                                          :playlist playlist
                                          :stream   stream})]
          {:config      config
           :library     library
           :playlist    playlist
           :http-server http-server
           :ice-conn    ice-conn
           :stream      stream}))
    (throw (Exception. "Could not find configuration file."))))
