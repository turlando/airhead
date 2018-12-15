(ns airhead.core
  (:gen-class)
  (:require [clojure.edn :as edn]
            [airhead.utils :as utils]
            [airhead.library :as library]
            [airhead.playlist :as playlist]
            [airhead.server :as server]))

(def ^:private config-file "airhead.edn")
(def ^:private config-dirs ["./"
                            "/etc/airhead/"
                            "/usr/local/etc/airhead/"])

(defn stop!
  [{:keys [config http-server]
    :as   args}]
  (server/stop! http-server)
  nil)

(defn start! []
  (let [config-path (utils/find-file-in-dirs config-file config-dirs)
        config      (edn/read-string (slurp config-path))
        lib-path    (-> config :library :path)
        lib         (library/open lib-path)
        playlist    (playlist/make-playlist)]
    (when-not config-path
      (throw (Exception. "Could not find configuration file.")))
    {:config      config
     :library     lib
     :playlist    playlist
     :http-server (server/start! {:config   config
                                  :library  lib
                                  :playlist playlist})}))
