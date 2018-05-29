(ns tusk.router
  (:require
   [com.stuartsierra.component :as c]
   [bidi.bidi :as b]
   [bidi.ring :refer [make-handler]]
   [ring.util.http-response :as res]
   [taoensso.timbre :as log]
   [tusk.router.protocols :as rrp]))

;; --------| ring router |--------

(defn- collect-route-config
  [component]
  (into []
        (comp
         (map val)
         (filter (and #(satisfies? b/RouteProvider %)
                      #(satisfies? rrp/ResourceProvider %))))
        component))

(defrecord RingRouter [routes handler]
  c/Lifecycle
  (start [{:keys [handler] :as this}]
    (if (some? handler)
      this
      (do (log/info "Creating ring router...")
          (let [route-configs     (collect-route-config this)
                routes            ["" (into {} (map b/routes) route-configs)]
                resources         (into {} (map rrp/resources) route-configs)
                bidi-handler      (make-handler routes resources)
                not-found-handler (-> (res/not-found)
                                      (res/content-type "text/plain")
                                      (constantly))
                handler           (fn [req]
                                    (or (bidi-handler req)
                                        (not-found-handler req)))]
            (assoc this :routes routes :handler handler)))))
  (stop [this]
    this))

(defn create-ring-router
  []
  (map->RingRouter {}))
