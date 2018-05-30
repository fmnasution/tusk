(ns tusk.router
  (:require
   [cljs.spec.alpha :as s]
   [cljs.core.async :as a]
   [com.stuartsierra.component :as c]
   [bidi.bidi :as b]
   [bidi.router :refer [start-router!]]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   [tusk.router.protocols :as rrp]
   [tusk.async :as as]))

;; --------| html router |--------

(defn- collect-routes
  [component]
  (into {}
        (comp
         (map val)
         (filter #(s/valid? ::rrp/route-provider %))
         (map b/routes))
        component))

(defn- router-callback
  [{:keys [location-chan] :as html-router}]
  (fn [location]
    (a/put! location-chan location)))

(defrecord HtmlRouter [location-chan default-location routes router]
  c/Lifecycle
  (start [{:keys [location-chan router] :as this}]
    (if (some? router)
      this
      (do (log/info "Starting html router...")
          (let [location-chan    (or location-chan (a/chan 100))
                default-location (or default-location {:handler ::default})
                routes           (collect-routes this)
                this             (assoc this
                                        :location-chan    location-chan
                                        :default-location default-location
                                        :routes           routes)
                router           (start-router!
                                  ["" routes]
                                  {:on-navigate      (router-callback this)
                                   :default-location default-location})]
            (assoc this :router router)))))
  (stop [{:keys [router] :as this}]
    (if (nil? router)
      this
      (do (log/info "Stopping html router...")
          (assoc this
                 :routes nil
                 :router nil)))))

(defn create-html-router
  ([{:keys [location-chan default-location] :as params}]
   (s/assert ::html-router-params params)
   (map->HtmlRouter {:location-chan    location-chan
                     :default-location default-location}))
  ([]
   (create-html-router {})))

;; --------| html router pipeliner |--------

(defn- process-location
  [{:keys [handler route-params] :as location}]
  (help/assoc-when {:location/handler handler}
                   :location/route-params (when (and (map? route-params)
                                                     (seq route-params))
                                            route-params)))

(defn- location->event
  [location]
  [:html-router/location location])

(defn create-html-router-location-pipeliner
  ([params]
   (let [xform-fn   (constantly
                     (comp
                      (map process-location)
                      (map location->event)))
         ex-handler (fn [error]
                      [:html-router/error
                       {:error error}
                       {:error? true}])]
     (-> params
         (assoc :xform-fn xform-fn
                :ex-handler ex-handler
                :message "Pipelining location from html router...")
         (as/create-channel-pipeliner))))
  ([]
   (create-html-router-location-pipeliner {})))

;; --------| spec |--------

(s/def ::location-chan help/chan?)

(s/def ::handler help/qualified-keyword?)

(s/def ::default-location (s/keys :opt-un [::handler]))

(s/def ::html-router-params (s/keys :opt-un [::location-chan
                                             ::default-location]))
