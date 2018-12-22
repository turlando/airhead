(ns airhead-frontend.core
  (:require [reagent.core :as r]
            [airhead-frontend.components :refer [page-component]]
            [airhead-frontend.state :as state]))

(def dev? ^boolean js/goog.DEBUG)

(defn maybe-dev-setup! []
  (when dev?
    (enable-console-print!)))

(defn mount-root []
  (r/render [page-component]
            (.getElementById js/document "app")))

(defn ^:export main []
  (maybe-dev-setup!)
  (mount-root))
