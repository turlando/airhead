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
;; HANDLERS                                                                   ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-info [request]
  (let [info    (-> request :config :info)
        icecast (-> request :config :icecast)]
    (ok-response
     {:name          (:name info)
      :greet_message (:greet-message info)
      :stream_url    (str (if (:https? icecast)"https://" "http://")
                          (:addr icecast)
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
  (let [lib    (-> request :library)
        file   (-> request :params (get "track"))
        result (library/add lib (:tempfile file))
        uuid   (-> result :tags :uuid)]
    (ok-response {:track uuid})))

(defn- get-playlist [request]
  (let [lib    (-> request :library)
        pl     (-> request :playlist)
        status (playlist/status pl)]
    (ok-response {:current (library/get-track lib (first status))
                  :next    (map #(library/get-track lib %) (rest status))})))

(defn- put-playlist [request]
  (let [lib    (-> request :library)
        pl     (-> request :playlist)
        id     (-> request :params :id)
        status (playlist/status pl)]
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
  (let [lib    (-> request :library)
        pl     (-> request :playlist)
        id     (-> request :params :id)
        status (playlist/status pl)]
    (cond
      (not-any? #{id} status) (bad-request-response
                               {:err "track_not_found"
                                :msg "No track found with such UUID."})
      :else                   (do (playlist/remove! pl id)
                                  (ok-response {})))))


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
      (compojure/DELETE "/:id" [] delete-playlist))))


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
