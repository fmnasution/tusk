(ns tusk.system
  (:require
   [com.stuartsierra.component :as c]
   [tusk.config :as cfg]
   [tusk.datastore :as dtst]))

(defn create-system-map
  []
  (c/system-map
   :config    (cfg/create-config
               {:source "resources/private/tusk/config.edn"
                :option {:profile :dev}})
   :datastore (c/using
               (dtst/create-datastore
                {:config-key :datastore})
               [:config])))
