(ns airhead.server
  (:require [clojure.data.json :as json]
            [org.httpkit.server :as server]
            [ring.middleware.params :as middleware.params]
            [ring.middleware.multipart-params :as middleware.multipart-params]
            [ring.middleware.json :as middleware.json]
            [compojure.core :as compojure]
            [airhead.utils :as utils]
            [airhead.library :as library]
            [airhead.playlist :as playlist]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; RESPONSE BOILERPLATE                                                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- ok-response [body]
  {:status 200
   :body   body})

(defn- bad-request-response [body]
  {:status 400
   :body   body})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WEBSOCKET HELPERS                                                          ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn notify-websocket-clients [clients data]
  (doseq [channel @clients]
    (server/send! channel (json/write-str data) false)))

(defn update-response [x]
  {:update x})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HANDLERS                                                                   ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-info [request]
  (let [info    (-> request :config :info)
        icecast (-> request :config :icecast)]
    (ok-response
     {:name          (:name info)
      :greet_message (:greet-message info)
      :stream_url    (str "http://" (:addr icecast)
                          ":" (:port icecast)
                          "/" (:mount icecast))})))

(defn- get-library [request]
  (let [lib (-> request :library)
        id  (-> request :params :id)
        q   (-> request :params (get "q"))]
    (cond
      (utils/not-nil? id)  (if-let [track (library/get-track lib id)]
                             (ok-response track)
                             (bad-request-response
                              {:err "uuid_not_valid"
                               :msg "No track found with such UUID."}))
      (utils/not-blank? q) (ok-response {:tracks (library/search lib q)})
      :else                (ok-response {:tracks (library/search lib)}))))

(defn- post-library [request]
  (let [lib        (-> request :library)
        ws-clients (-> request :ws-clients)
        file       (-> request :params (get "track"))
        result     (library/add lib (:tempfile file))
        uuid       (-> result :tags :uuid)]
    (future @(:future result)
            (notify-websocket-clients
             ws-clients
             (update-response "library")))
    (ok-response {:track uuid})))

(defn- get-playlist [request]
  (let [lib    (-> request :library)
        pl     (-> request :playlist)
        status (playlist/status pl)]
    (ok-response {:current (library/get-track lib (first status))
                  :next    (map #(library/get-track lib %) (rest status))})))

(defn- put-playlist [request]
  (let [lib        (-> request :library)
        pl         (-> request :playlist)
        ws-clients (-> request :ws-clients)
        id         (-> request :params :id)
        status     (playlist/status pl)]
    (cond
      (some #{id} status)               (bad-request-response
                                         {:err "duplicate"
                                          :msg (str "The track is already "
                                                    "present in the playlist.")})
      (nil? (library/get-track lib id)) (bad-request-response
                                         {:err "track_not_found"
                                          :msg "No track found with such UUID."})
      :else                             (do (playlist/push! pl id)
                                            (future (notify-websocket-clients
                                                     ws-clients
                                                     (update-response "playlist")))
                                            (ok-response {})))))

(defn- delete-playlist [request]
  (let [lib    (-> request :library)
        pl     (-> request :playlist)
        ws-clients (-> request :ws-clients)
        id     (-> request :params :id)
        status (playlist/status pl)]
    (cond
      (not-any? #{id} status) (bad-request-response
                               {:err "track_not_found"
                                :msg "No track found with such UUID."})
      :else                   (do (playlist/remove! pl id)
                                  (future (notify-websocket-clients
                                           ws-clients
                                           (update-response "playlist")))
                                  (ok-response {})))))

(defn- get-ws [request]
  (let [clients (-> request :ws-clients)]
    (server/with-channel request channel
      (swap! clients conj channel)
      (server/on-close channel
                       (fn [status]
                         (swap! clients #(remove channel %)))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ROUTES                                                                     ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(compojure/defroutes routes
  (compojure/context "/api" []
    (compojure/GET "/info" [] get-info)
    (compojure/context "/library" []
      (compojure/GET "/" [] get-library)
      (compojure/GET "/:id" [] get-library)
      (compojure/POST "/" [] post-library))
    (compojure/context "/playlist" []
      (compojure/GET "/" [] get-playlist)
      (compojure/PUT "/:id" [] put-playlist)
      (compojure/DELETE "/:id" [] delete-playlist))
    (compojure/GET "/ws" [] get-ws)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MIDDLEWARES                                                                ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- add-cors [response]
  (assoc-in response [:headers "Access-Control-Allow-Origin"] "*"))

(defn- wrap-cors [handler]
  (fn wrap-cors*
    ([request]
     (add-cors (handler request)))
    ([request respond raise]
     (handler request #(respond (add-cors %)) raise))))

(defn- wrap-assoc-request [handler k v]
  (fn wrap-assoc-request*
    ([request]
     (handler (assoc request k v)))
    ([request respond raise]
     (handler (assoc request k v) #(respond %) raise))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; START/STOP                                                                 ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start!
  [{:keys [config library playlist]
    :as   args}]
  (server/run-server
   (-> routes
       (wrap-assoc-request :config config)
       (wrap-assoc-request :library library)
       (wrap-assoc-request :playlist playlist)
       (wrap-assoc-request :ws-clients (atom []))
       middleware.params/wrap-params
       middleware.multipart-params/wrap-multipart-params
       middleware.json/wrap-json-response
       wrap-cors)
   {:ip       (-> config :bind :addr)
    :port     (-> config :bind :port)
    :max-body 100000000 ;; 100Mb
    :join?    false}))

(defn stop! [s]
  (s :timeout 0))
