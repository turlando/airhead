(ns airhead-frontend.requests
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs-http.client :as http]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<! chan pipeline pipe]]
            [airhead-frontend.state :refer [app-state update-state!]]
            [goog.string :as gstring]
            [goog.string.format]))

(defn get-info! []
  (go (let [response (<! (http/get "/api/info"))]
        (update-state! :info (:body response)))))

(defn get-playlist! []
  (go (let [response (<! (http/get "/api/playlist"))
            body     (response :body)]
        (update-state! :playlist    (body :next))
        (update-state! :now-playing (body :current)))))

(defn playlist-add! [id]
  (http/put (str "/api/playlist/" id)))

(defn playlist-remove! [id]
  (http/delete (str "/api/playlist/" id)))

(defn get-library! []
  (go (let [response (<! (http/get "/api/library"
                                   {:query-params {"q" (@app-state :query)}}))]
        (update-state! :library (get-in response [:body :tracks])))))

(defn playlist-skip! [id]
  (http/delete (str "/api/playlist/"
                    (-> @app-state :now-playing :uuid))))

(defn upload! [form]
  (let [progress-chan (chan 1
                            (comp (filter #(= (% :direction) :upload))
                                  (map    #(dissoc % :direction))))

        response-chan (http/post "/api/library"
                                 {:body (js/FormData. form)
                                  :progress progress-chan})
        out-chan (chan)]

    (pipe response-chan out-chan)
    (pipe progress-chan out-chan)
    out-chan))

(defn get-updates! []
  (get-info!)
  (get-playlist!)
  (get-library!))

(get-updates!)

(go
  (let [location js/window.location
        host     location.host
        protocol location.protocol
        ws-path (str (if (= protocol "https:")
                       "wss://" "ws://")
                     host "/api/ws")
        {:keys [ws-channel]} (<! (ws-ch ws-path {:format :json-kw}))]
    (loop []
      (let [{:keys [message]} (<! ws-channel)]
        ;; TODO: parse message
        (get-updates!)
        (when message (recur))))))
