(ns tusk.websocket.routes
  (:require
   [bidi.bidi :as b]
   [tusk.router.protocols :as rrp]
   #?@(:clj [[tusk.websocket.resources :as wsr]])))

;; --------| websocket route config |--------

(defrecord WebsocketServerRouteConfig []
  b/RouteProvider
  (routes [_]
    {"/chsk" ::ring}))

#?(:clj (extend-protocol rrp/ResourceProvider
          WebsocketServerRouteConfig
          (resources [_]
            {::ring wsr/ring-resource})))

(defn create-websocket-server-resource-config
  []
  (map->WebsocketServerRouteConfig {}))
