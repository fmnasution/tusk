(ns tusk.system
  (:require
   [com.stuartsierra.component :as c]
   [taoensso.sente.packers.transit :refer [get-transit-packer]]
   [tusk.config :as cf]
   [tusk.datastore :as dts]
   [tusk.websocket :as ws]
   [tusk.async :as as]
   [tusk.web :as w]
   [tusk.router :as rr]
   [tusk.middleware :as m]
   [tusk.websocket.routes :as wsrs]
   [tusk.web.middleware :as wm]
   [tusk.web.routes :as wrs]
   [tusk.async.handler]
   #?@(:clj [[taoensso.sente.server-adapters.http-kit
              :refer [get-sch-adapter]]])))

;; --------| system |---------

(defn create-system-map
  [option]
  (c/system-map
   :config
   (cf/create-config {:source "resources/private/tusk/config.edn"
                      :option option})

   :web-server
   (-> (w/create-web-server {:config-key :web-server})
       (c/using [:config])
       (c/using {:ring-handler    :ring-router
                 :ring-middleware :middleware-collector}))

   :ring-router
   (c/using
    (rr/create-ring-router)
    [:web-route-config
     :websocket-server-route-config])

   :web-route-config
   (wrs/create-web-route-config)

   :websocket-server-route-config
   (wsrs/create-websocket-server-resource-config)

   :middleware-collector
   (c/using
    (m/create-middleware-collector)
    [:web-middleware-container])

   :web-middleware-container
   (c/using
    (wm/create-web-middleware-container)
    [:websocket-server :ring-router])

   :datastore
   (c/using
    (dts/create-datastore {:config-key :datastore})
    [:config])

   :datastore-tx-monitor
   (c/using
    (dts/create-datastore-tx-monitor)
    [:datastore])

   :datastore-tx-pipeliner
   (c/using
    (dts/create-datastore-tx-pipeliner)
    {:from :datastore-tx-monitor
     :to   :event-dispatcher})

   :event-dispatcher
   (as/create-event-dispatcher)

   :event-consumer
   (c/using
    (as/create-event-consumer)
    [:event-dispatcher])

   :effect-executor
   (c/using
    (as/create-effect-executor)
    [:event-consumer :event-dispatcher])

   #?@(:clj  [:websocket-server
              (ws/create-websocket-server
               {:server-adapter (get-sch-adapter)
                :server-option  {:packer (get-transit-packer)}})

              :websocket-server-event-pipeliner
              (c/using
               (ws/create-websocket-server-event-pipeliner)
               {:from :websocket-server
                :to   :event-dispatcher})]
       :cljs [:websocket-client
              (ws/create-websocket-client
               {:server-uri    "/chsk"
                :client-option {:packer (get-transit-packer)}})

              :websocket-client-event-pipeliner
              (c/using
               (ws/create-websocket-client-event-pipeliner)
               {:from :websocket-client
                :to   :event-dispatcher})]) ))
