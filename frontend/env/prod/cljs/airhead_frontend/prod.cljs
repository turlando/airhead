(ns airhead-frontend.prod
  (:require [airhead-frontend.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
