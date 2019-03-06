(ns airhead.playlist
  (:require [airhead.utils :as utils]))

(defn mk-playlist []
  (ref clojure.lang.PersistentQueue/EMPTY))

(defn- get-current* [playlist]
  (peek @playlist))

(defn- get-next* [playlist]
  (rest @playlist))

(defn- enqueued?* [playlist item]
  "Not thread safe."
  (some #(= item %) @playlist))

(defn- remove!* [playlist item]
  (if-not (enqueued?* playlist item)
    :not-found
    (if (= item (get-current* playlist))
      (do (alter playlist pop)
          :skipped)
      ;; Nasty hack to avoid type coercion.
      (do (alter playlist #(apply conj clojure.lang.PersistentQueue/EMPTY
                                  (remove #{item} %)))
          :success))))

(defn get-playlist [playlist]
  (dosync
   {:current (get-current* playlist)
    :next    (get-next* playlist)}))

(defn get-current [playlist]
  (dosync
   (get-current* playlist)))

(defn get-next [playlist]
  (dosync
   (get-next* playlist)))

(defn enqueue! [playlist item]
  (dosync
   (if (enqueued?* playlist item)
     :duplicate
     (do (alter playlist conj item)
         :success))))

(defn dequeue! [playlist]
  (dosync
   (if-not (get-current* playlist)
     :empty
     (do (alter playlist pop)
         :success))))

(defn remove!
  ([playlist item]
   (dosync
    (remove!* playlist item)))
  ([playlist item skip-fn]
   (dosync
    (let [r (remove!* playlist item)]
      (if (= :skipped r)
        (do (skip-fn)
            r)
        r)))))
