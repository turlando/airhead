(ns airhead.core
  (:gen-class)
  (:require [clojure.repl]
            [clojure.tools.logging :as log]
            [clojure.edn :as edn]
            [airhead.utils :as utils]
            [airhead.library :as library]
            [airhead.playlist :as playlist]
            [airhead.libshout :as libshout]
            [airhead.server :as server]
            [airhead.stream :as stream]))

(def ^:private config-file "airhead.edn")
(def ^:private config-dirs ["/usr/local/etc/airhead/"
                            "/etc/airhead/"
                            "./"])

(defn stop!
  [{:keys [ice-conn stream http-server]
    :as   args}]
  (log/info "Stopping Airhead")
  (when-not (nil? http-server)
    (server/stop! args))
  (when-not (nil? stream)
    (stream/stop! stream))
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
              icecast     (:icecast config)
              info        (:info config)
              ice-conn    (do (libshout/init-lib!)
                              (libshout/open icecast))
              stream      (stream/mk-stream
                           {:ice-conn ice-conn
                            :library  library
                            :playlist playlist
                            :random?  (-> config :library :auto-dj?)})
              http-server (server/start!
                           {:addr        (-> config :bind :addr)
                            :port        (-> config :bind :port)
                            :static-path (-> config :http :static-path)
                            :info        {:name       (:name info)
                                          :message    (:greet-message info)
                                          :stream-url (str "http://" (:addr icecast)
                                                           ":" (:port icecast)
                                                           "/" (:mount icecast))}
                            :config      config
                            :library     library
                            :playlist    playlist
                            :stream      stream})]
          {:config      config
           :library     library
           :playlist    playlist
           :http-server http-server
           :ice-conn    ice-conn
           :stream      stream}))
    (throw (Exception. "Could not find configuration file."))))

(defn -main []
  (let [s (start!)]
    (clojure.repl/set-break-handler!
     (fn [_]
       (stop! s)
       (shutdown-agents)
       (System/exit 0)))))
