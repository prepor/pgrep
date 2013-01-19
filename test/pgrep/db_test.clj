(ns pgrep.db-test
  (:use
   pgrep.db
   clojure.test
   korma.core)
  (:require
   [pgrep.core-test :as core]))

(use-fixtures :each core/wrap-reset-database core/wrap-test-table)

(deftest get-id-type-t
  (let [type (get-id-type core/videos)]
    (is (= "varchar(256)" type))))

(deftest get-table-name-t
  (is (= "videos_pgrep_videos_rep" (get-table-name core/videos-rep))))

(deftest create-table-t
  (create-table core/videos-rep)
  (let [columns-seq (select columns (where {:table_name (get-table-name core/videos-rep)}))
        columns (zipmap (map (comp keyword :column_name) columns-seq) columns-seq)]
    (are [n udt_name] (= (get-in columns [n :udt_name]) udt_name)
         :id  "varchar"
         :state "int4"
         :sync_at "timestamp")))
