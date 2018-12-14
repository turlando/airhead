(ns airhead.utils-test
  (:require [clojure.test :refer :all]
            [airhead.test :as sut]))

(deftest test-seek
  ;; always nil for nil or empty coll/seq
  (are [x] (= (seek pos? x) nil)
    nil
    () [] {} #{}
    (lazy-seq [])
    (into-array []))

  (are [x y] (= x y)
    nil (seek nil nil)

    1 (seek pos? [1])
    1 (seek pos? [1 2])

    nil (seek pos? [-1])
    nil (seek pos? [-1 -2])
    2   (seek pos? [-1 2])
    1   (seek pos? [1 -2])
    ;; does not consume whole sequence
    10  (seek #(>= % 10) (range))

    :a (seek #{:a} '(:a :b))
    :a (seek #{:a} #{:a :b})

    ;; can find false
    false (seek false? [false])
    false (seek false? [true false])
    false (seek false? [true false true])
    nil   (seek false? [true true])

    ;; not-found value
    ::nf (seek false? [true] ::nf)
    ::nf (seek pos? [] ::nf)
    ::nf (seek nil nil ::nf)))
