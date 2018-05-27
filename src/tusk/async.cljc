(ns tusk.async
  (:require
   [com.stuartsierra.component :as c]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   [tusk.async.protocols :as asnc.prt]
   #?@(:clj  [[clojure.spec.alpha :as s]
              [clojure.core.async :as a :refer [go-loop]]]
       :cljs [[cljs.spec.alpha :as s]
              [cljs.core.async :as a]]))
  #?(:cljs
     (:require-macros
      [cljs.core.async.macros :refer [go-loop]])))

;; --------| event dispatcher |---------

(defrecord EventDispatcher [event-chan started?]
  asnc.prt/ISink
  (sink-chan [event-dispatcher]
    (:event-chan event-dispatcher))

  c/Lifecycle
  (start [{:keys [event-chan started?] :as this}]
    (if started?
      this
      (do (log/info "Starting event dispatcher...")
          (let [event-chan (or event-chan (a/chan 100))]
            (assoc this :event-chan event-chan :started? true)))))
  (stop [this]
    this))

(defn create-event-dispatcher
  ([{:keys [event-chan] :as params}]
   (s/assert ::event-dispatcher-params params)
   (map->EventDispatcher {:event-chan event-chan
                          :started?   false}))
  ([]
   (create-event-dispatcher {})))

;; --------| event consumer |---------

(defrecord EventConsumer [event-dispatcher stop-chan]
  c/Lifecycle
  (start [{:keys [event-dispatcher stop-chan] :as this}]
    (if (some? stop-chan)
      this
      (do (log/info "Starting event consumer...")
          (let [event-chan (:event-chan event-dispatcher)
                stop-chan  (a/chan)]
            (go-loop []
              (let [[event chan] (a/alts! [event-chan stop-chan] :priority true)
                    stop?        (or (= stop-chan chan) (nil? event))]
                (when-not stop?
                  (println event)
                  (recur))))
            (assoc this :stop-chan stop-chan)))))
  (stop [{:keys [event-dispatcher stop-chan] :as this}]
    (if (nil? stop-chan)
      this
      (do (log/info "Stopping event consumer...")
          (a/close! stop-chan)
          (assoc this :stop-chan nil)))))

(defn create-event-consumer
  []
  (map->EventConsumer {}))

;; --------| channel pipeliner |---------

(defn- pipeline!
  [{:keys [parallelism to xform-fn from ex-handler] :as this}]
  (let [parallelism (or parallelism 1)
        to-chan     (asnc.prt/sink-chan to)
        xform       (xform-fn this)
        from-chan   (asnc.prt/source-chan from)
        close?      false]
    (a/pipeline parallelism to-chan xform from-chan close? ex-handler)
    ::ok))

(defrecord ChannelPipeliner [parallelism
                             to
                             xform-fn
                             from
                             ex-handler
                             message
                             started?]
  c/Lifecycle
  (start [{:keys [message started?] :as this}]
    (if started?
      this
      (do (log/info (or message "Starting channel pipeliner..."))
          (pipeline! this)
          (assoc this :started? true))))
  (stop [this]
    this))

(defn create-channel-pipeliner
  [{:keys [parallelism xform-fn ex-handler message] :as params}]
  (s/assert ::channel-pipeliner-params params)
  (map->ChannelPipeliner {:xform-fn   xform-fn
                          :message    message
                          :ex-handler ex-handler
                          :started?   false}))

;; --------| spec |---------

(s/def ::event-chan help/chan?)

(s/def ::event-dispatcher-params (s/keys :opt-un [::event-chan]))

(s/def ::xform-fn fn?)

(s/def ::ex-handler fn?)

(s/def ::parallelism help/pos-int?)

(s/def ::message help/nblank-str?)

(s/def ::channel-pipeliner-params (s/keys :req-un [::xform-fn ::ex-handler]
                                          :opt-un [::parallelism ::message]))
