(ns tusk.web.routes
  (:require
   [bidi.bidi :as b]
   #?@(:clj [[tusk.router.protocols :as rrp]
             [tusk.web.resources :as wr]])))

;; --------| web route config |--------

(defrecord WebFilesRouteConfig []
  b/RouteProvider
  (routes [_]
    {"/resources/public" {true ::asset}
     "/"                 ::index}))

#?(:clj (extend-protocol rrp/ResourceProvider
          WebFilesRouteConfig
          (resources [_]
            {::asset wr/asset-resource
             ::index wr/index-resource})))

(defn create-web-files-route-config
  []
  (map->WebFilesRouteConfig {}))
