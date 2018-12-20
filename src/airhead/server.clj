(ns airhead.server
  (:require [clojure.data.json :as json]
            [org.httpkit.server :as server]
            [ring.middleware.params :as middleware.params]
            [ring.middleware.multipart-params :as middleware.multipart-params]
            [ring.middleware.json :as middleware.json]
            [compojure.core :as compojure]
            [compojure.route :as compojure.route]
            [airhead.utils :as utils]
            [airhead.library :as library]
            [airhead.playlist :as playlist]
            [airhead.stream :as stream]))

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
                                            (ok-response {})))))

(defn- delete-playlist [request]
  (let [lib        (-> request :library)
        pl         (-> request :playlist)
        ws-clients (-> request :ws-clients)
        stream     (-> request :stream)
        id         (-> request :params :id)
        status     (playlist/status pl)]
    (cond
      (not-any? #{id} status)             (bad-request-response
                                           {:err "track_not_found"
                                            :msg "No track found with such UUID."})
      ;; TODO: this is not thread safe.
      (= id (first (playlist/status pl))) (do (stream/skip! stream)
                                              (ok-response {}))
      :else                               (do (playlist/remove! pl id)
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
  [{:keys [config library playlist stream]
    :as   args}]
  (let [ws-clients (atom [])]
    (add-watch (library/get-atom library) :notify-ws-library
               (fn [key ref old-value new-value]
                 (notify-websocket-clients ws-clients
                                           (update-response "library"))))
    (add-watch playlist :notify-ws-playlist
               (fn [key ref old-value new-value]
                 (notify-websocket-clients ws-clients
                                           (update-response "playlist"))))
    (server/run-server
     (-> (compojure/routes
          routes
          (when-let [static-path (-> config :http :static-path)]
            (compojure.route/files "/" {:root static-path})))
         (wrap-assoc-request :config config)
         (wrap-assoc-request :library library)
         (wrap-assoc-request :playlist playlist)
         (wrap-assoc-request :stream stream)
         (wrap-assoc-request :ws-clients ws-clients)
         middleware.params/wrap-params
         middleware.multipart-params/wrap-multipart-params
         middleware.json/wrap-json-response
         wrap-cors)
     {:ip       (-> config :bind :addr)
      :port     (-> config :bind :port)
      :max-body 100000000 ;; 100Mb
      :join?    false})))

(defn stop! [{:keys [library playlist http-server]}]
  (remove-watch (library/get-atom library) :notify-ws-library)
  (remove-watch playlist :notify-ws-playlist)
  (http-server :timeout 0))
