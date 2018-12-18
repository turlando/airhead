(defproject airhead "0.1.0-SNAPSHOT"
  :description ""
  :url "https://github.com/turlando/airhead"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.json "0.2.6"]
                 [http-kit "2.3.0"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-json "0.4.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [compojure "1.6.1"]
                 [com.ealva/ealvatag "0.4.2"]
                 [clj-native "0.9.5"]]

  :main ^:skip-aot airhead.core
  :global-vars {*warn-on-reflection* true}

  :target-path "target/%s"

  :profiles {:uberjar {:aot :all}})
