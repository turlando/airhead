(defproject airhead-frontend "0.1.0-SNAPSHOT"
  :description ""
  :url "https://github.com/turlando/airhead"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.8.0"
  :plugins [[lein-doo "0.1.10"]
            [lein-cljsbuild "1.1.7"]
            [deraen/lein-less4j "0.6.2"]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.439"
                  :exclusions [com.google.errorprone/error_prone_annotations
                               com.google.code.findbugs/jsr305
                               com.fasterxml.jackson.core/jackson-core]]
                 [reagent "0.8.1"]
                 [cljs-http "0.1.43"]
                 [jarohen/chord "0.8.1"]
                 [markdown-clj "0.9.99"]]

  :clean-targets ^{:protect false} ["target"
                                    "node_modules"
                                    "package.json"
                                    "package-lock.json"
                                    "resources/public/js"
                                    "resources/public/css"
                                    "figwheel_server.log"
                                    ".nrepl-port"
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
                    :external-config      {:devtools/config {:features-to-install :all}}}
     :figwheel     {:websocket-url "ws://[[server-hostname]]:[[server-port]]/figwheel-ws"
                    :on-jsload     "airhead-frontend.core/mount-root"}}

    {:id           "min"
     :source-paths ["src/cljs"]
     :compiler     {:main            airhead-frontend.core
                    :closure-defines {goog.DEBUG false}
                    :output-to       "resources/public/js/app.js"
                    :optimizations   :advanced
                    :elide-asserts   true
                    :pretty-print    false}}]}

  :less {:source-paths ["src/less" "node_modules"]
         :target-path  "resources/public/css"}

  :profiles
  {:dev {:plugins        [[lein-figwheel "0.5.17"]]
         :dependencies   [[figwheel-sidecar "0.5.17"
                           :exclusions [[org.clojure/tools.nrepl]
                                        [args4j]]]
                          [cider/piggieback "0.3.10"
                           :exclusions [[org.clojure/tools.logging]
                                        [args4j]]]
                          [binaryage/devtools "0.9.10"]]
         :source-paths   ["src/clj/dev"]
         :resource-paths ["resources" "node_modules"]
         :repl-options   {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
         :less           {:source-map true}
         :figwheel       {:server-port 8081
                          :css-dirs    ["resources/public/css"]}}}

  :aliases
  {"dev"   ["with-profile" "+dev" "do"
            ["clean"]
            ["cljsbuild" "once" "dev"]
            ["less4j" "once"]
            ["figwheel" "dev"]]
   "build" ["with-profile" "-dev" "do"
            ["clean"]
            ["less4j" "once"]
            ["cljsbuild" "once" "min"]]})
