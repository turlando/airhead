(defproject airhead-frontend "0.1.0-SNAPSHOT"
  :description "Airhead clojurescript frontend"
  :url "https://github.com/edne/airhead-frontend"
  :license {:name "2-Clause BSD License"
            :url "https://opensource.org/licenses/BSD-2-Clause"}

  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.9.562" :scope "provided"]
                 [reagent "0.6.2"]
                 [cljs-http "0.1.43"]
                 [jarohen/chord "0.8.1"]
                 [markdown-clj "0.9.99"]]

  :plugins [[lein-cljsbuild "1.1.5"]
            [cljs-simple-cache-buster "0.1.1"]
            [lein-doo "0.1.6"]
            [lein-figwheel "0.5.10"]]

  :min-lein-version "2.5.0"

  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]

  :resource-paths ["public"]

  :figwheel {:http-server-root "."
             :nrepl-port 7002
             :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]
             :css-dirs ["public/css"]}

  :cljs-simple-cache-buster {:cljsbuild-id ["app" "test" "release"]
                             :template-file "template/index.html"
                             :output-to "public/index.html"}

  :cljsbuild {:builds {:app
                       {:source-paths ["src" "env/dev/cljs"]
                        :compiler
                        {:main "airhead-frontend.dev"
                         :output-to "public/js/app.js"
                         :output-dir "public/js/out"
                         :asset-path   "js/out"
                         :source-map true
                         :optimizations :none
                         :pretty-print  true}
                        :figwheel
                        {:open-urls ["http://localhost:3449/index.html"]}}

                       :test
                       {:source-paths ["src" "test" "env/dev/cljs"]
                        :compiler {:main airhead-frontend.test-runner
                                   :output-to  "public/js/test.js"
                         :source-map true
                         :optimizations :none
                         :pretty-print  true}}

                       :release
                       {:source-paths ["src" "env/prod/cljs"]
                        :compiler
                        {:output-to "public/js/app.js"
                         :output-dir "public/js/release"
                         :asset-path   "js/out"
                         :optimizations :advanced
                         :pretty-print false}}}}

  :aliases {"release" ["do" "clean" ["cljsbuild" "once" "release"]]}

  :profiles {:dev {:dependencies [[figwheel-sidecar "0.5.10"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [com.cemerick/piggieback "0.2.2-SNAPSHOT"]]}})
