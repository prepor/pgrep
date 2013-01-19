(ns pgrep.core-test
  (:use clojure.test
        pgrep.core
        korma.core
        korma.db)
  (:require
   [clojure.java.jdbc :as sql]
   [pgrep.db :as db]))

(def pg_info {:subprotocol "postgresql"
              :subname "//localhost:5432/postgres"
              :user "postgres"
              :password "postgres"})

(defdb pg {:subprotocol "postgresql"
           :subname "//localhost:5432/pgrep"
           :user "postgres"
           :password "postgres"})


(defentity videos)

(defrep videos-rep
  videos
  (on-delete [id]
             :ok)
  (on-update [row]
             :ok))

(defn reset-database
  []
  (with-db pg_info
    (db/raw-commands
     "DROP DATABASE IF EXISTS pgrep"
     "CREATE DATABASE pgrep")))

(defn wrap-reset-database
  [f]
  (reset-database)
  (f))

(defn test-table
  []
  (with-db pg
    (sql/create-table
     :videos
     [:id "varchar(256)" "PRIMARY KEY"]
     [:title "varchar(256)"]
     [:duration :int])))

(defn wrap-test-table
  [f]
  (test-table)
  (f))

(defn wrap-init-rep
  [f]
  (init-rep videos-rep)
  (f))

(defn wrap-complete
  [f]
  (reset-database)
  (test-table)
  (init-rep videos-rep)
  (f))

(deftest defrep-macro
  (is (= videos (:entity videos-rep)))
  (is (= "videos-rep" (:name videos-rep)))
  (are [param value] (= value (get-in videos-rep [:self-entity param]))
       :name "videos-rep-entity"
       :table "videos_pgrep_videos_rep")
  (are [f] (fn? (get-in videos-rep [:callbacks f]))
       :on-delete
       :on-update))

(deftest init-rep-t
  (letfn [(select-ids [] (set (map :id (select (:self-entity videos-rep)))))]
    (reset-database)
    (test-table)
    (testing "Init data should be replicated"
      (insert videos (values [{:id "mail/1" :title "madonna burning up" :duration 100}
                              {:id "mail/2" :title "bumer" :duration 500}]))
      (init-rep videos-rep)
      (is (= #{"mail/1" "mail/2"} (select-ids))))
    (testing "New data should be replicated"
      (insert videos (values {:id "mail/3" :title "hoho!" :duration 300}))
      (is (= #{"mail/1" "mail/2" "mail/3"} (select-ids))))))
