(ns pgrep.db
  (:use
   [korma.db :only [_default with-db]]
   [slingshot.slingshot :only [throw+]]
   korma.core
   clojure.core.strint)
  (:require
   [clojure.java.jdbc :as sql]))

(def IN_SYNC_STATE 2r01)
(def NOT_SYNC_STATE 2r00)

(defentity columns
  (table :information_schema.columns))

(defn db
  [entity]
  (or (:db entity) @_default))

(defn get-id-type
  [entity]
  (let [id-column (name (:pk entity))
        res (-> (select columns
                        (where {:table_name (:table entity) :column_name id-column}))
                first)]
    (if res
      (case (:udt_name res)
        "varchar" (format "varchar(%d)" (:character_maximum_length res))
        ("int2" "int4" "int8") :int)
      (throw+ {:type ::bad-env}))))

(defn get-table-name
  [{:keys [self-entity]}]
  (:table self-entity))

(defn raw-commands
  [& commands]
  (with-open [stmt (.createStatement (sql/connection))]
    (doseq [cmd commands]
      (.addBatch stmt cmd))
    (.executeBatch stmt)))

(defn create-table
  [{:keys [entity name] :as rep}]
  (let [entity-name (:name entity)]
    (with-db (db entity)
      (let [table-name (get-table-name rep)
            id-type (get-id-type entity)]
        (sql/create-table
         table-name
         [:id id-type "PRIMARY KEY"]
         [:state :int :default NOT_SYNC_STATE]
         [:sync_at :timestamp])
        (sql/do-commands
         (<< "CREATE INDEX ~{table-name}_state_sync_at ON ~{table-name} (state,sync_at)"))))))

(defn do-commands
  [{:keys [entity]} & commands]
  (with-db (db entity)
    (apply sql/do-commands commands)))

(defn get-funcs
  [{:keys [entity] :as rep}]
  (let [table (get-table-name rep)
        source-table (:table entity)
        source-id-col (name (:pk entity))]
    [(<< "CREATE OR REPLACE FUNCTION ~{table}_sync(p_id ~{source-table}.~{source-id-col}%TYPE) RETURNS boolean AS $$
BEGIN
  UPDATE ~{table} SET state = ~{NOT_SYNC_STATE} WHERE id = p_id AND state <> ~{NOT_SYNC_STATE};
  INSERT INTO ~{table} (id) SELECT p_id WHERE NOT EXISTS (SELECT 1 FROM ~{table} WHERE id = p_id);
  RETURN TRUE;
EXCEPTION WHEN unique_violation THEN
  RETURN TRUE;
END;
$$ LANGUAGE plpgsql")
     (<<
      "CREATE OR REPLACE FUNCTION ~{table}_trigger() RETURNS trigger AS $$
DECLARE
  id ~{source-table}.~{source-id-col}%TYPE;
BEGIN
  IF (TG_OP = 'DELETE') THEN
    id := OLD.~{source-id-col};
  ELSE
    id := NEW.~{source-id-col};
  END IF;
  PERFORM ~{table}_sync(id);
  RETURN NULL;
END;
$$ LANGUAGE plpgsql")]))

(defn create-funcs
  [rep]
  (let [commands (get-funcs rep)]
    (apply do-commands rep commands)))

(defn get-triggers
  [{:keys [entity] :as rep}]
  (let [table (get-table-name rep)
        source-table (:table entity)]
    [(<< "CREATE TRIGGER ~{table}_trigger_all
  AFTER INSERT OR UPDATE OR DELETE ON ~{source-table}
  FOR EACH ROW
  EXECUTE PROCEDURE ~{table}_trigger()")]))

(defn create-triggers
  [rep]
  (apply do-commands rep (get-triggers rep)))

(defn get-init-stmt
  [{:keys [entity] :as rep}]
  (let [table (get-table-name rep)
        source-table (:table entity)]
    (<< "SELECT count(res) FROM (SELECT ~{table}_sync(id) FROM ~{source-table}) res")))

(defn init-data
  [{:keys [self-entity entity] :as rep}]
  (exec-raw (db entity) (get-init-stmt rep) :results))

(defn reset
  [rep]
  (let [table (get-table-name rep)]
    (do-commands rep (<< "TRUNCATE TABLE ~{table}"))
    (init-data rep)))
