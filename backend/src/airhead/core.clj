(ns airhead.core
  (:gen-class)
  (:require [clojure.repl]
            [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
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

(s/def ::start-args (s/keys :req-un [::addr ::port ::static-path
                                     ::library-path ::auto-dj?
                                     ::info ::icecast]))
(s/def ::start-ret (s/keys :req-un [::library ::playlist ::http-server
                                    ::ice-conn ::stream]))

(defn start!
  [{:keys [addr port static-path library-path auto-dj? info icecast]
    :as   args}]
  {:pre  [(s/valid? ::start-args args)]
   :post [(s/valid? ::start-ret %)]}

  (log/info "Starting Airhead.")
  (let [library  (library/open (io/file library-path))
        playlist (playlist/mk-playlist)

        ice-conn   (do (libshout/init-lib!)
                       (libshout/open icecast))
        stream     (stream/mk-stream
                    {:ice-conn ice-conn
                     :library  library
                     :playlist playlist
                     :random?  auto-dj?})
        stream-url (str "http://" (:addr icecast)
                        ":" (:port icecast)
                        "/" (:mount icecast))

        server-config {:addr        addr
                       :port        port
                       :static-path static-path
                       :info        (assoc info :stream-url stream-url)
                       :library     library
                       :playlist    playlist
                       :stream      stream}
        http-server   (server/start! server-config)]

    {:library     library
     :playlist    playlist
     :http-server http-server
     :ice-conn    ice-conn
     :stream      stream}))

(defn stop!
  [{:keys [ice-conn stream http-server]
    :as   args}]
  {:pre [(s/valid? ::start-ret args)]}

  (log/info "Stopping Airhead")
  (when-not (nil? http-server)
    (server/stop! http-server))
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

(defn -main []
  (let [config-path (utils/find-file-in-dirs config-file config-dirs)
        config      (edn/read-string (slurp config-path))
        s           (start! config)]
    (clojure.repl/set-break-handler!
     (fn [_]
       (stop! s)
       (shutdown-agents)
       (System/exit 0)))))
