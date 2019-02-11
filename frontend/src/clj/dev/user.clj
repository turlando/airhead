(ns user
  (:use [figwheel-sidecar.repl-api :as ra]))

(defn start! [] (ra/start-figwheel!))
(defn stop! [] (ra/stop-figwheel!))
(defn cljs-repl! [] (ra/cljs-repl "dev"))
