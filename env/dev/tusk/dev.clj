(ns tusk.dev
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [com.stuartsierra.component :as c]
   [com.stuartsierra.component.repl :as cr]
   [tusk.system :as st]
   [tusk.figwheel :as fw]))

(defn toggle-assertion!
  []
  (if (s/check-asserts?)
    (s/check-asserts false)
    (s/check-asserts true)))

(defn- create-dev-system-map
  [option]
  (assoc (st/create-system-map option)
         :figwheel-server
         (c/using
          (fw/create-figwheel-server {:config-key :figwheel-server})
          [:config])))

(defn create-system!
  []
  (cr/set-init (constantly
                (create-dev-system-map {:profile :dev}))))

(defn start!
  []
  (cr/start))

(defn stop!
  []
  (cr/stop))

(defn restart!
  []
  (cr/reset))
