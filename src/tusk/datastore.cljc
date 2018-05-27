(ns tusk.datastore
  (:require
   [com.stuartsierra.component :as c]
   [datascript.core :as dts]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   [tusk.async.protocols :as asnc.prt]
   [tusk.async :as asnc]
   #?@(:clj  [[clojure.spec.alpha :as s]
              [clojure.core.async :as a]
              [datomic.api :as dtm]]
       :cljs [[cljs.core.async :as a]
              [cljs.spec.alpha :as s]])))

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
  (start [{:keys [config config-key conn] :as this}]
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
            (assoc this :kind kind :conn conn)))))
  (stop [{:keys [kind conn] :as this}]
    (if (nil? conn)
      this
      (do (log/info "Stopping datastore...")
          #?(:clj (when (= :datomic kind)
                    (dtm/release conn)))
          (assoc this :kind nil :conn nil)))))

(defn create-datastore
  [{:keys [config-key] :as params}]
  (s/assert ::datastore-params params)
  (map->Datastore {:config-key config-key}))

;; --------| datastore tx monitor |--------

(defn- monitor-tx!
  [{:keys [datastore tx-report-chan]}]
  (let [{:keys [kind conn]} datastore]
    (case kind
      :datomic
      #?(:clj  (let [active?_        (atom true)
                     tx-report-queue (dtm/tx-report-queue conn)]
                 (future
                   (while @active?_
                     (let [tx-report (.take tx-report-queue)]
                       (a/put! tx-report-chan tx-report))))
                 #(reset! active?_ false))
         :cljs nil)

      :datascript
      (do (dts/listen! conn #(a/put! tx-report-chan %) ::tx-report)
          #(dts/unlisten! conn ::tx-report)))))

(defrecord DatastoreTxMonitor [datastore tx-report-chan stopper]
  asnc.prt/ISource
  (source-chan [datastore-tx-monitor]
    (:tx-report-chan datastore-tx-monitor))

  c/Lifecycle
  (start [this]
    (let [{:keys [tx-report-chan stopper]} this]
      (if (some? stopper)
        this
        (do (log/info "Monitoring transaction on datastore...")
            (let [tx-report-chan (or tx-report-chan (a/chan 100))
                  this           (assoc this :tx-report-chan tx-report-chan)
                  stopper        (monitor-tx! this)]
              (assoc this :stopper stopper))))))
  (stop [this]
    (let [{:keys [stopper]} this]
      (if (nil? stopper)
        this
        (do (log/info "No longer monitors transaction on datastore...")
            (stopper)
            (assoc this :stopper nil))))))

(defn create-datastore-tx-monitor
  ([{:keys [tx-report-chan] :as params}]
   (s/assert ::datastore-tx-monitor-params params)
   (map->DatastoreTxMonitor {:tx-report-chan tx-report-chan}))
  ([]
   (create-datastore-tx-monitor {})))

;; --------| datastore tx pipeliner |--------

(defn- tx-report->event
  [tx-report]
  [::tx-report tx-report])

(defn create-datastore-tx-pipeliner
  ([params]
   (let [xform-fn   #(map tx-report->event)
         ex-handler (fn [error]
                      [::error {:error error} {:error? true}])]
     (-> params
         (assoc :xform-fn   xform-fn
                :ex-handler ex-handler
                :message    "Pipelining tx report...")
         (asnc/create-channel-pipeliner))))
  ([]
   (create-datastore-tx-pipeliner {})))

;; --------| spec |--------

#?(:clj (s/def ::uri help/nblank-str?))

#?(:clj (s/def ::datomic-config (s/keys :req-un [::uri])))

(s/def ::kind #{#?(:clj :datomic) :datascript})

(s/def ::datastore-config (s/keys :req-un [::kind]))

(s/def ::config-key keyword?)

(s/def ::datastore-params (s/keys :req-un [::config-key]))

(s/def ::tx-report-chan help/chan?)

(s/def ::datastore-tx-monitor-params (s/keys :opt-un [::tx-report-chan]))
