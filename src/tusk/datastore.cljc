(ns tusk.datastore
  (:require
   [com.stuartsierra.component :as c]
   [datascript.core :as dts]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   #?@(:clj [[datomic.api :as dtm]])))

;; --------| datastore |--------

(defn- create-datomic-conn!
  [{:keys [uri]}]
  (dtm/create-database uri)
  (dtm/connect uri))

(defn- create-datascript-conn!
  [_]
  (dts/create-conn))

(defrecord Datastore [config config-key kind conn]
  c/Lifecycle
  (start [this]
    (let [{:keys [config config-key conn]} this]
      (if (some? conn)
        this
        (do (log/info "Starting datastore...")
            (let [config (get-in config [:value config-key])
                  conn   (case kind
                           :datomic
                           #?(:clj  (create-datomic-conn! config)
                              :cljs nil)

                           :datascript
                           (create-datascript-conn! config))]
              (assoc this :kind kind :conn conn))))))
  (stop [this]
    (let [{:keys [kind conn]} this]
      (if (nil? conn)
        this
        (do (log/info "Stopping datastore...")
            #?(:clj (when (= :datomic kind)
                      (dtm/release conn)))
            (assoc this :kind nil :conn nil))))))

;; ----| API |----

(defn create-datastore
  [{:keys [config-key] :as params}]
  (map->Datastore {:config-key config-key}))
