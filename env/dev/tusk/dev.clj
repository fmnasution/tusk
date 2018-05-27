(ns tusk.dev
  (:require
   [clojure.repl :refer :all]
   [com.stuartsierra.component.repl :as cr]
   [tusk.system :as st]))

(defn create-system!
  []
  (cr/set-init (constantly (st/create-system-map {:profile :dev}))))

(defn delete-system!
  []
  (cr/set-init (constantly nil)))

(defn start!
  []
  (cr/start))

(defn stop!
  []
  (cr/stop))

(defn restart!
  []
  (cr/reset))
