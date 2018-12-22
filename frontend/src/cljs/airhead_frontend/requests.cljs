(ns airhead-frontend.requests
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs-http.client :as http]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<! chan pipeline pipe]]
            [airhead-frontend.state :refer [app-state update-state!]]
            [goog.string :as gstring]
            [goog.string.format]))

(def dev? ^boolean js/goog.DEBUG)

(defn api-url [s]
  (str (.-protocol js/window.location)
       "//"
       (if dev?
         "127.0.0.1:8080"
         (.-host js/window.location))
       "/api/" s))

(defn ws-url []
  (str (if (= (.-protocol js/window.location) "https:")
         "wss://" "ws://")
       (if dev?
         "127.0.0.1:8080"
         (.-host js/window.location))
       "/api/ws"))

(defn get-info! []
  (go (let [response (<! (http/get (api-url "info")
                                   {:with-credentials? false}))]
        (js/console.log (clj->js response))
        (update-state! :info (:body response)))))

(defn get-playlist! []
  (go (let [response (<! (http/get (api-url "playlist")
                                   {:with-credentials? false}))
            body     (response :body)]
        (update-state! :playlist    (body :next))
        (update-state! :now-playing (body :current)))))

(defn playlist-add! [id]
  (http/put (api-url (str "playlist/" id)
                     {:with-credentials? false})))

(defn playlist-remove! [id]
  (http/delete (api-url (str "playlist/" id)
                        {:with-credentials? false})))

(defn get-library! []
  (go (let [response (<! (http/get (api-url "library")
                                   {:with-credentials? false
                                    :query-params {"q" (@app-state :query)}}))]
        (update-state! :library (get-in response [:body :tracks])))))

(defn playlist-skip! [id]
  (http/delete (api-url (str "playlist/" (-> @app-state :now-playing :uuid))
                        {:with-credentials? false})))

(defn upload! [form]
  (let [progress-chan (chan 1
                            (comp (filter #(= (% :direction) :upload))
                                  (map    #(dissoc % :direction))))

        response-chan (http/post (api-url "library")
                                 {:with-credentials? false
                                  :body     (js/FormData. form)
                                  :progress progress-chan})
        out-chan      (chan)]

    (pipe response-chan out-chan)
    (pipe progress-chan out-chan)
    out-chan))

(defn get-updates! []
  (get-info!)
  (get-playlist!)
  (get-library!))

(get-updates!)

(go
  (let [{:keys [ws-channel]} (<! (ws-ch (ws-url) {:format :json-kw}))]
    (loop []
      (let [{:keys [message]} (<! ws-channel)]
        ;; TODO: parse message
        (get-updates!)
        (when message (recur))))))
