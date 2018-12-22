(ns airhead-frontend.state
  (:require [reagent.core :as r]))

(def app-state (r/atom {:info     {:name          ""
                                   :greet_message ""
                                   :stream_url    nil}

                        :playlist    []
                        :now-playing nil

                        :library []

                                        ; TODO: move this inside the component
                        :sort-field :title
                        :ascending  true}))

(defn update-state! [k v]
  (swap! app-state assoc k v))
