(ns airhead.playlist-test
  (:require [clojure.test :refer :all]
            [airhead.playlist :as sut]))

(deftest test-mk-playlist
  (let [q (sut/mk-playlist)]
    (is (instance? clojure.lang.PersistentQueue @q)
        "Queue is not a clojure.lang.PersistentQueue")))

(deftest test-playlist-access
  (testing "Making a playlist"
    (let [q (sut/mk-playlist)]

      (testing "enqueuing four elements"
        (is (= :success (sut/enqueue! q :a)))
        (is (= :success (sut/enqueue! q :b)))
        (is (= :success (sut/enqueue! q :c)))
        (is (= :success (sut/enqueue! q :d)))

        (testing "accessing all elements"
          (is (= :a (sut/get-current q))
              "Cannot get current element")
          (is (= '(:b :c :d) (sut/get-next q))
              "Cannot get next elements"))

        (testing "dequeuing"
          (sut/dequeue! q)
          (is (= :b (sut/get-current q))
              "Cannot get current element")
          (is (= '(:c :d) (sut/get-next q))
              "Cannot get next elements")))))

  (testing "Making a playlist"
    (let [q (sut/mk-playlist)]

      (testing "enqueuing an element"
        (is (= :success (sut/enqueue! q :a)))

        (testing "dequeuing the single element"
          (is (= :success (sut/dequeue! q)))

          (is (instance? clojure.lang.PersistentQueue @q)
              "Queue not a PersistentQueue anymore")
          (is (nil? (sut/get-current q))
              "Queue should be empty but it isn't")
          (is (empty? (sut/get-next q))
              "Queue should be empty but it isn't"))))))

(deftest playlist-remove
  (testing "Making a playlist"
    (let [q (sut/mk-playlist)]

      (testing "adding one element"
        (is (= :success (sut/enqueue! q :a)))

        (testing "removing the first element in the queue"
          (is (= :skipped (sut/remove! q :a)))

          (is (instance? clojure.lang.PersistentQueue @q)
              "Queue not a PersistentQueue anymore")
          (is (nil? (sut/get-current q))
              "Queue should be empty but it isn't")
          (is (empty? (sut/get-current q))
              "Queue should be empty but it isn't")))))

  (testing "Making a playlist"
    (let [q (sut/mk-playlist)]

      (testing "adding two elements"
        (is (= :success (sut/enqueue! q :a)))
        (is (= :success (sut/enqueue! q :b)))

        (testing "removing the second element in the queue"
          (is (= :success (sut/remove! q :b))))))))
