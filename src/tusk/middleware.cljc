(ns tusk.middleware
  (:require
   [com.stuartsierra.component :as c]
   [taoensso.timbre :as log]
   [tusk.middleware.protocols :as mp]))

(defn- inject-component
  [component entry]
  (if (vector? entry)
    (replace {:component component} entry)
    entry))

(defn- as-middleware
  [entry]
  (if (vector? entry)
    #(apply (first entry) % (rest entry))
    entry))

(defn compose-middleware
  [middlewares component]
  (apply comp (into []
                    (comp
                     (map #(inject-component component %))
                     (map as-middleware))
                    middlewares)))

;; --------| middleware collector |--------

(defn- collect-middleware-config
  [component]
  (into {}
        (comp
         (map val)
         (filter #(satisfies? mp/MiddlewareContainer %))
         (map (fn [middleware-container]
                (let [id (mp/id middleware-container)]
                  {id {:middleware (mp/middleware middleware-container)
                       :requires   (mp/requires middleware-container)}}))))
        component))

(defn- order-middleware
  [container middleware-config ids]
  (reduce (fn [container id]
            (let [current   (get middleware-config id)
                  requires  (:requires current)
                  container (cond-> container
                              (seq requires)
                              (order-middleware middleware-config requires))]
              (if (contains? (:done container) id)
                container
                (-> container
                    (update :middleware into (:middleware current))
                    (update :done conj id)))))
          container
          ids))

(defn- construct-wrapper
  [middleware-config middleware-collector]
  (let [ids (keys middleware-config)]
    (-> {:middleware []
         :done       #{}}
        (order-middleware middleware-config ids)
        (:middleware)
        (compose-middleware middleware-collector))))

(defrecord MiddlewareCollector [wrapper]
  c/Lifecycle
  (start [{:keys [wrapper] :as this}]
    (if (some? wrapper)
      this
      (do (log/info "Creating middleware collector...")
          (let [middleware-config (collect-middleware-config this)
                wrapper           (construct-wrapper middleware-config this)]
            (assoc this :wrapper wrapper)))))
  (stop [this]
    this))

(defn create-middleware-collector
  []
  (map->MiddlewareCollector {}))
