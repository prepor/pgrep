(ns pgrep.handler-test
  (:use
   pgrep.core
   pgrep.handler
   clojure.test
   korma.core)
  (:require
   [pgrep.core-test :as core]))

(use-fixtures :each core/wrap-complete)

(deftest get-changes-t
  (insert core/videos (values [{:id "mail/1"}
                               {:id "mail/2"}]))
  (let [res (get-changes core/videos-rep)]
    (is (= #{"mail/1" "mail/2"} (set (keys res))))
    (are [id] (= (get-in res [id :id]) id)
         "mail/1"
         "mail/2"))
  (testing "should not be returning again"
    (is (= 0 (count (get-changes core/videos-rep)))))
  (testing "should return new change"
    (insert core/videos (values {:id "mail/3"}))
    (is (= #{"mail/3"} (-> (get-changes core/videos-rep) keys set)))))



(deftest handle-change-t
  (let [updates-atom (atom [])
        deletes-atom (atom [])
        rep (-> core/videos-rep
                (on-update [row] (swap! updates-atom conj row))
                (on-delete [id] (swap! deletes-atom conj id)))]
    (handle-change rep ["mail/1" {:id "mail/1"}])
    (handle-change rep ["mail/2" nil])
    (is (= [{:id "mail/1"}] @updates-atom))
    (is (= ["mail/2"] @deletes-atom))))

(deftest changes-tick-t
  (let [updates-atom (atom #{})
        deletes-atom (atom #{})
        rep (-> core/videos-rep
                (on-update [row] (swap! updates-atom conj row))
                (on-delete [id] (swap! deletes-atom conj id)))]
    (insert core/videos (values [{:id "mail/1"}
                                 {:id "mail/2"}]))
    (changes-tick rep)
    (is (= #{{:id "mail/1" :title nil :duration nil} {:id "mail/2" :title nil :duration nil}} @updates-atom))
    (is (= #{} @deletes-atom))
    (is (= 0 (count (get-changes rep))))
    (delete core/videos (where {:id "mail/2"}))
    (changes-tick rep)
    (is (= 2 (count @updates-atom)))
    (is (= #{"mail/2"} @deletes-atom))))
