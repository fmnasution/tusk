(ns tusk.system
  (:require
   [com.stuartsierra.component :as c]
   [taoensso.sente.packers.transit :refer [get-transit-packer]]
   [tusk.config :as cfg]
   [tusk.datastore :as dtst]
   [tusk.websocket :as ws]
   #?@(:clj [[taoensso.sente.server-adapters.http-kit
              :refer [get-sch-adapter]]])))

(defn create-system-map
  []
  (c/system-map
   :config               (cfg/create-config
                          {:source "resources/private/tusk/config.edn"
                           :option {:profile :dev}})
   :datastore            (c/using
                          (dtst/create-datastore
                           {:config-key :datastore})
                          [:config])
   :datastore-tx-monitor (c/using
                          (dtst/create-datastore-tx-monitor)
                          [:datastore])
   #?@(:clj  [[:websocket-server
               (ws/create-websocket-server
                {:server-adapter (get-sch-adapter)
                 :server-option  {:packer (get-transit-packer)}})]]
       :cljs [[:websocket-client
               (ws/create-websocket-client
                {:server-uri "/chsk"
                 :client-option {:packer (get-transit-packer)}})]]) ))
