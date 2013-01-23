(ns pgrep.core
  (:require
   [korma.core :as korma]
   [pgrep.db :as db]
   [pgrep.handler :as handler]))

(defmacro on-update
  [rep & body]
  `(assoc-in ~rep [:callbacks :on-update] (fn ~@body)))

(defmacro on-delete
  [rep & body]
  `(assoc-in ~rep [:callbacks :on-delete] (fn ~@body)))

(defn make-rep
  [n entity self-entity]
  {:name n
   :entity entity
   :callbacks {}
   :self-entity self-entity})

(defmacro defrep
  "Define replication rules. name should be correct postgres table name; entity
  is Korma entity"
  [n entity & body]
  (let [entity-name (symbol (str (name n) "-entity"))]
    `(let [formatted-name# (clojure.string/replace ~(name n) "-" "_")
           table-name# (str (:table ~entity) "_pgrep_" formatted-name#)]
       (do (korma/defentity ~entity-name
             (korma/table table-name#)
             (korma/has-one ~entity {:fk :id})) ; korma assumes entity is a var, so define it
           (def ~n (-> (make-rep ~(name n) ~entity ~entity-name)
                       ~@body))))))

(defn init-rep
  [rep]
  (db/create-table rep)
  (db/create-funcs rep)
  (db/create-triggers rep)
  (db/init-data rep))

(defn reset-rep
  [rep]
  (db/reset rep))

(defn start-rep
  [rep]
  (handler/listen-changes rep))

(defn start-rep-cleaner
  [rep]
  (handler/rep-cleaner-loop rep))
