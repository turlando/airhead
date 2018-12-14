(ns airhead.server
  (:require [clojure.data.json :as json]
            [org.httpkit.server :as server]
            [ring.middleware.params :as middleware.params]
            [ring.middleware.json :as middleware.json]
            [compojure.core :as compojure]
            [airhead.utils :as utils]
            [airhead.library :as library]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; RESPONSE BOILERPLATE                                                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- ok-response [body]
  {:status 200
   :body   body})

(defn bad-request-response [body]
  {:status 400
   :body body})


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

(defn post-library [])
(defn get-playlist[])
(defn put-playlist [])
(defn delete-playlist [])


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
      (compojure/PUT "/" [] put-playlist)
      (compojure/DELETE "/" [] delete-playlist))))


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
  [{:keys [config library]
    :as   args}]
  (server/run-server
   (-> routes
       (wrap-assoc-request :config config)
       (wrap-assoc-request :library library)
       middleware.params/wrap-params
       middleware.json/wrap-json-response
       wrap-cors)
   {:ip    (-> config :bind :addr)
    :port  (-> config :bind :port)
    :join? false}))

(defn stop! [s]
  (s :timeout 0))
