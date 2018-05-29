(ns tusk.web
  (:require
   [clojure.spec.alpha :as s]
   [com.stuartsierra.component :as c]
   [org.httpkit.server :refer [run-server]]
   [ring.util.http-response :as res]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]))

;; --------| web server |--------

(defrecord WebServer [config config-key ring-handler ring-middleware server]
  c/Lifecycle
  (start [{:keys [config config-key ring-handler ring-middleware server]
           :as   this}]
    (if (some? server)
      this
      (do (log/info "Starting web server...")
          (let [config          (as-> config <>
                                  (get-in <> [:value config-key])
                                  (s/assert ::web-server-config <>))
                middleware      (:wrapper ring-middleware identity)
                default-handler (-> (res/service-unavailable)
                                    (res/content-type "text/plain")
                                    (constantly))
                handler         (:handler ring-handler default-handler)
                server          (run-server (middleware handler) config)]
            (assoc this :server server)))))
  (stop [{:keys [server] :as this}]
    (if (nil? server)
      this
      (do (log/info "Stopping web server...")
          (server :timeout 100)
          (assoc this :server nil)))))

(defn create-web-server
  [{:keys [config-key] :as params}]
  (s/assert ::web-server-params params)
  (map->WebServer {:config-key config-key}))

;; --------| spec |--------

(s/def ::port help/pos-int?)

(s/def ::web-server-config (s/keys :req-un [::port]))

(s/def ::config-key keyword?)

(s/def ::web-server-params (s/keys :req-un [::config-key]))
