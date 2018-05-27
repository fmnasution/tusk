(ns tusk.dev
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [com.stuartsierra.component.repl :as cr]
   [tusk.system :as st]))

(defn toggle-assertion!
  []
  (if (s/check-asserts?)
    (s/check-asserts false)
    (s/check-asserts true)))

(defn create-system!
  []
  (cr/set-init (constantly (st/create-system-map {:profile :dev}))))

(defn start!
  []
  (cr/start))

(defn stop!
  []
  (cr/stop))

(defn restart!
  []
  (cr/reset))
