(ns tusk.system
  (:require
   [com.stuartsierra.component :as c]
   [taoensso.sente.packers.transit :refer [get-transit-packer]]
   [tusk.config :as cf]
   [tusk.datastore :as dts]
   [tusk.websocket :as ws]
   [tusk.async :as as]
   [tusk.router :as rr]
   [tusk.middleware :as m]
   [tusk.websocket.routes :as wsrs]
   [tusk.web.routes :as wrs]
   [tusk.async.handler]
   #?@(:clj  [[taoensso.sente.server-adapters.http-kit
               :refer [get-sch-adapter]]
              [tusk.web :as w]
              [tusk.web.middleware :as wm]]
       :cljs [[tusk.web.ajax :as wa]
              [tusk.element :as el]])))

;; --------| system |---------

(defn create-system-map
  [option]
  (c/system-map
   :config
   (cf/create-config {:source "resources/private/tusk/config.edn"
                      :option option})

   :web-files-route-config
   (wrs/create-web-files-route-config)

   :websocket-server-route-config
   (wsrs/create-websocket-server-resource-config)

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

              :websocket-server-pipeliner
              (c/using
               (ws/create-websocket-server-pipeliner)
               {:from :websocket-server
                :to   :event-dispatcher})

              :ring-router
              (c/using
               (rr/create-ring-router)
               [:web-files-route-config
                :websocket-server-route-config])

              :web-server
              (-> (w/create-web-server {:config-key :web-server})
                  (c/using [:config])
                  (c/using {:ring-handler    :ring-router
                            :ring-middleware :middleware-collector}))

              :middleware-collector
              (c/using
               (m/create-middleware-collector)
               [:web-middleware-container
                :websocket-server
                :ring-router])

              :web-middleware-container
              (wm/create-web-middleware-container)]
       :cljs [:websocket-client
              (ws/create-websocket-client
               {:server-uri    "/chsk"
                :client-option {:packer (get-transit-packer)}})

              :websocket-client-pipeliner
              (c/using
               (ws/create-websocket-client-pipeliner)
               {:from :websocket-client
                :to   :event-dispatcher})

              :html-router
              (rr/create-html-router)

              :html-router-pipeliner
              (c/using
               (rr/create-html-router-pipeliner)
               {:from :html-router
                :to   :event-dispatcher})

              :server-ajax-caller
              (c/using
               (wa/create-server-ajax-caller)
               [:websocket-client])

              :server-ajax-pipeliner
              (c/using
               (wa/create-server-ajax-pipeliner)
               {:from :server-ajax-caller
                :to   :event-dispatcher})

              :element
              (c/using
               (el/create-element
                {:config-key :element})
               [:config :datastore :event-dispatcher])]) ))
