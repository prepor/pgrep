(ns pgrep.handler
  (:use
   korma.core
   clojure.core.strint)
  (:require
   [pgrep.db :as db]))

(def CHANGES_LIMIT 50)
(defn get-changes
  [{:keys [self-entity entity] :as rep}]
  (let [table (:table self-entity)
        source-table (:table entity)
        res (exec-raw (db/db entity) (<< "UPDATE \"~{table}\" SET state = ~{db/IN_SYNC_STATE}, sync_at = now() WHERE state = ~{db/NOT_SYNC_STATE} RETURNING id") :results)
        ids (map :id res)
        source-res (select entity (where {(:pk entity) [in ids]}))
        source-res-map (zipmap (map :id source-res) source-res)]
    (zipmap ids (map #(get source-res-map %) ids))))

(defn in-sync
  [{:keys [self-entity]} changes]
  (delete self-entity (where {:id [in (keys changes) :state db/IN_SYNC_STATE]})))

(defn handle-change
  [rep [id row]]
  (let [[callback-name fn-callback] (if (nil? row)
                                      [:on-delete (fn [clb] (clb id))]
                                      [:on-update (fn [clb] (clb row))])
        callback (get-in rep [:callbacks callback-name])]
    (when callback
      (fn-callback callback))))

(def NOTHING_SLEEP 100)
(defn changes-tick
  [rep]
  (let [changes (get-changes rep)]
    (if (seq changes)
      (do
        (doall (map (partial handle-change rep) changes))
        (in-sync rep changes))
      (Thread/sleep NOTHING_SLEEP))))

(defn listen-changes
  [rep]
  (changes-tick rep)
  (recur rep))

(def SYNC_TIMEOUT 3600) ; timeout for sync handle operation in seconds
(def CLEANER_INTERVAL 60000) ; in ms
(defn rep-cleaner-tick
  [{:keys [self-entity]}]
  (update self-entity (set-fields {:state db/IN_SYNC_STATE}) (where (< :sync_at (raw (<< "NOW() - INTERVAL '~{SYNC_TIMEOUT} seconds'")))))
  (Thread/sleep CLEANER_INTERVAL))

(defn rep-cleaner-loop
  [rep]
  (rep-cleaner-tick rep)
  (recur rep))
