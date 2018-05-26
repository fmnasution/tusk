(ns tusk.datastore
  (:require
   [com.stuartsierra.component :as c]
   [datascript.core :as dts]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   #?@(:clj  [[clojure.spec.alpha :as s]
              [datomic.api :as dtm]]
       :cljs [[cljs.spec.alpha :as s]])))

;; --------| datastore |--------

#?(:clj (defn- create-datomic-conn!
          [{:keys [uri] :as config}]
          (s/assert ::datomic-config config)
          (dtm/create-database uri)
          (dtm/connect uri)))

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
            (let [config (as-> config <>
                           (get-in <> [:value config-key])
                           (s/assert ::datastore-config <>))
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
  (s/assert ::datastore-params params)
  (map->Datastore {:config-key config-key}))

;; --------| spec |--------

#?(:clj (s/def ::uri help/nblank-str?))

#?(:clj (s/def ::datomic-config (s/keys :req-un [::uri])))

(s/def ::kind #{#?(:clj :datomic) :datascript})

(s/def ::datastore-config (s/keys :req-un [::kind]))

(s/def ::config-key keyword?)

(s/def ::datastore-params (s/keys :req-un [::config-key]))
