(defproject airhead-frontend "0.1.0-SNAPSHOT"
  :description ""
  :url "https://github.com/turlando/airhead"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.8.0"
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-less "1.7.5"]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.439"]
                 [reagent "0.8.1"]
                 [cljs-http "0.1.43"]
                 [jarohen/chord "0.8.1"]
                 [markdown-clj "0.9.99"]]


  :clean-targets ^{:protect false} ["resources/public/js"
                                    "resources/public/css"
                                    "target"
                                    ".nrepl-port"
                                    "figwheel_server.log"
                                    ".lein-repl-history"
                                    ".rebel_readline_history"]

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs"]
     :compiler     {:main                 airhead-frontend.core
                    :closure-defines      {goog.DEBUG true}
                    :optimizations        :none
                    :output-to            "resources/public/js/app.js"
                    :output-dir           "resources/public/js/out"
                    :asset-path           "js/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload]
                    :external-config      {:devtools/config
                                           {:features-to-install    [:formatters :hints]
                                            :fn-symbol              "F"
                                            :print-config-overrides true}}}
     ;; figwheel client config
     :figwheel     {:websocket-url "ws://[[server-hostname]]:[[server-port]]/figwheel-ws"
                    :on-jsload     "airhead-frontend.core/mount-root"}}

    {:id           "min"
     :source-paths ["src/cljs"]
     :compiler     {:main            airhead-frontend.core
                    :closure-defines {goog.DEBUG false}
                    :optimizations   :advanced
                    :output-to       "resources/public/js/app.js"
                    :output-dir      "resources/public/js/min"
                    :elide-asserts   true
                    :pretty-print    false}}]}

  :less {:source-paths ["src/less"]
         :target-path  "resources/public/css"}

  :profiles
  {:dev
   {:plugins      [[lein-figwheel "0.5.17"]]
    :dependencies [[figwheel-sidecar "0.5.17"]
                   [cider/piggieback "0.3.10"]
                   [binaryage/devtools "0.9.9"]]
    :source-paths ["src/clj"]
    :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
    ;; figwheel server config
    :figwheel     {:server-port 8081
                   :css-dirs    ["resources/public/css"]}}}

  :aliases
  {"dev"   ["with-profile" "+dev" "do"
            ["clean"]
            ["cljsbuild" "once" "dev"]
            ["less" "once"]
            ["figwheel" "dev"]]
   "build" ["with-profile" "-dev" "do"
            ["clean"]
            ["less" "once"]
            ["cljsbuild" "once" "min"]]})

