(ns airhead-frontend.core
  (:require [reagent.core :as r]
            [airhead-frontend.components :refer [page-component]]))

(defn mount-root []
  (r/render [page-component] (.getElementById js/document "app")))

(defn init! []
  (mount-root))
