# pgrep

Application level replication for PostgreSQL. It uses triggers and own table for queue changes.

## Usage

```clojure
(use 'korma.core 'korma.db 'pgrep.core)
(require '[clojure.java.jdbc :as sql])

(defdb pg {:subprotocol "postgresql"
           :subname "//localhost:5432/pgrep"
           :user "postgres"
           :password "postgres"})

(with-db pg
    (sql/create-table
     :videos
     [:id "varchar(256)" "PRIMARY KEY"]
     [:title "varchar(256)"]
     [:duration :int]))
	 
(defentity videos)

(insert videos (values [{:id "mail/1" :title "madonna burning up" :duration 300}
                        {:id "mail/2" :title "starcraft zerg vs protos" :duration 350}]))

(defrep videos-rep
  videos              ; it can be any Korma's entity
  (on-update [row] (println "UPDATE!" row))
  (on-delete [id] (println "DELETE!" id)))
  
(init-rep videos-rep) ; will create table, func, triggers and add exists data to queue

(alter-var-root #'*out* (constantly *out*)) ; for REPL's
(.start (Thread. (partial start-rep videos-rep)))

; UPDATE! {:duration 300, :title madonna burning up, :id mail/1}
; UPDATE! {:duration 350, :title starcraft zerg vs protos, :id mail/2}

(delete videos (where {:id "mail/2"}))

; DELETE! mail/2

; You can try execute sql directly:
; insert into videos values ('mail/2', 'starik!', 400);
; Result will be same
;
; UPDATE! {:duration 400, :title starik!, :id mail/2}

```
## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
