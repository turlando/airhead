(ns airhead.server
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [clojure.data.json :as json]
            [org.httpkit.server :as httpkit]
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
    (httpkit/send! channel (json/write-str data) false)))

(defn update-response [x]
  {:update x})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HANDLERS                                                                   ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-info [request]
  (let [info (:info request)]
    (ok-response
     {:name          (:name info)
      :greet_message (:greet-message info)
      :stream_url    (:stream-url info)})))

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
  (let [library  (-> request :library)
        playlist (-> request :playlist)
        p        (playlist/get-playlist playlist)]
    (ok-response {:current (library/get-track library (:current p))
                  :next    (map #(library/get-track library %) (:next p))})))

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
    (httpkit/with-channel request channel
      (swap! clients conj channel)
      (httpkit/on-close channel
                        (fn [status]
                          (swap! clients #(remove #{channel} %)))))))


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
  (-> response
      (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
      (assoc-in [:headers "Access-Control-Allow-Methods"] "GET, POST, PUT, DELETE")))

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

(s/def ::name string?)
(s/def ::message string?)
(s/def ::stream-url string?)

(s/def ::info (s/keys :req-un [::name ::message ::stream-url]))

(s/def ::addr string?)
(s/def ::port integer?)
(s/def ::static-path (s/nilable string?))

(s/def ::start-args (s/keys :req-un [::addr ::port ::static-path ::info
                                     ::library ::playlist ::stream]))
(s/def ::start-ret (s/keys ::req-un [::http-server ::library ::playlist]))

(defn start!
  [{:keys [addr port static-path info
           library playlist stream]
    :as   args}]
  {:pre  [(s/valid? ::start-args args)]
   :post [(s/valid? ::start-ret %)]}

  (let [ws-clients  (atom [])
        http-server (httpkit/run-server
                     (-> (compojure/routes
                          routes
                          (when static-path
                            (compojure.route/files "/" {:root static-path})))
                         (wrap-assoc-request :info info)
                         (wrap-assoc-request :library library)
                         (wrap-assoc-request :playlist playlist)
                         (wrap-assoc-request :stream stream)
                         (wrap-assoc-request :ws-clients ws-clients)
                         middleware.params/wrap-params
                         middleware.multipart-params/wrap-multipart-params
                         middleware.json/wrap-json-response
                         wrap-cors)
                     {:ip       addr
                      :port     port
                      :max-body 100000000 ;; 100Mb
                      :join?    false})]

    (add-watch (library/get-atom library) :notify-ws-library
               (fn [key ref old-value new-value]
                 (notify-websocket-clients ws-clients
                                           (update-response "library"))))
    (add-watch playlist :notify-ws-playlist
               (fn [key ref old-value new-value]
                 (notify-websocket-clients ws-clients
                                           (update-response "playlist"))))

    {:http-server http-server
     :library     library
     :playlist    playlist}))

(defn stop!
  [{:keys [http-server library playlist]
    :as args}]
  {:pre [(s/valid? ::start-ret args)]}

  (remove-watch (library/get-atom library) :notify-ws-library)
  (remove-watch playlist :notify-ws-playlist)
  (http-server :timeout 0)

  nil)
