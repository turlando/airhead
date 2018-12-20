(ns airhead-frontend.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [cljs.test :refer-macros [run-tests]]
            [airhead-frontend.core-test]))

;; This isn't strictly necessary, but is a good idea depending
;; upon your application's ultimate runtime engine.
(enable-console-print!)

(doo-tests 'airhead-frontend.core-test)
