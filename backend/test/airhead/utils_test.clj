(ns airhead.utils-test
  (:require [clojure.test :refer :all]
            [airhead.utils :as sut]))

(deftest test-seek
  ;; Taken from https://dev.clojure.org/jira/browse/CLJ-2056
  ;; always nil for nil or empty coll/seq
  (are [x] (= (sut/seek pos? x) nil)
    nil
    () [] {} #{}
    (lazy-seq [])
    (into-array []))

  (are [x y] (= x y)
    nil (sut/seek nil nil)

    1 (sut/seek pos? [1])
    1 (sut/seek pos? [1 2])

    nil (sut/seek pos? [-1])
    nil (sut/seek pos? [-1 -2])
    2   (sut/seek pos? [-1 2])
    1   (sut/seek pos? [1 -2])
    ;; does not consume whole sequence
    10  (sut/seek #(>= % 10) (range))

    :a (sut/seek #{:a} '(:a :b))
    :a (sut/seek #{:a} #{:a :b})

    ;; can find false
    false (sut/seek false? [false])
    false (sut/seek false? [true false])
    false (sut/seek false? [true false true])
    nil   (sut/seek false? [true true])

    ;; not-found value
    ::nf (sut/seek false? [true] ::nf)
    ::nf (sut/seek pos? [] ::nf)
    ::nf (sut/seek nil nil ::nf)))
