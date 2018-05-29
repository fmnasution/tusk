(ns tusk.web.routes
  (:require
   [bidi.bidi :as b]
   [tusk.router.protocols :as rrp]
   #?@(:clj [[tusk.web.resources :as wr]])))

;; --------| web route config |--------

(defrecord WebRouteConfig []
  b/RouteProvider
  (routes [_]
    {"/" ::index}))

#?(:clj (extend-protocol rrp/ResourceProvider
          WebRouteConfig
          (resources [_]
            {::index wr/index-resource})))

(defn create-web-route-config
  []
  (map->WebRouteConfig {}))
