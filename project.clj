(defproject pgrep "0.1.0-SNAPSHOT"
  :description "Application level replication for Postgresql"
  :url "http://github.com/prepor/pgrep"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [korma "0.3.0-beta15"]
                 [postgresql "9.0-801.jdbc4"]
                 [slingshot "0.10.3"]
                 [org.clojure/core.incubator "0.1.2"]])
