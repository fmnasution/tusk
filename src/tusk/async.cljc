(ns tusk.async
  (:require
   [com.stuartsierra.component :as c]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   [tusk.async.protocols :as asp]
   #?@(:clj  [[clojure.spec.alpha :as s]
              [clojure.core.async :as a :refer [go-loop]]]
       :cljs [[cljs.spec.alpha :as s]
              [cljs.core.async :as a]]))
  #?(:cljs
     (:require-macros
      [cljs.core.async.macros :refer [go-loop]])))

;; --------| event dispatcher |---------

(defrecord EventDispatcher [event-chan started?]
  asp/ISink
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

(defn- go-consume-event!
  [{:keys [event-dispatcher effect-chan stop-chan]
    :as   event-consumer}]
  (let [event-chan (:event-chan event-dispatcher)]
    (go-loop []
      (help/when-let [chans        [event-chan stop-chan]
                      [event chan] (a/alts! chans :priority true)
                      continue?    (and (not= stop-chan chan) (some? event))]
        ;; TODO: implement this
        (recur)))
    ::ok))

(defrecord EventConsumer [event-dispatcher effect-chan stop-chan]
  c/Lifecycle
  (start [{:keys [effect-chan stop-chan] :as this}]
    (if (some? stop-chan)
      this
      (do (log/info "Starting event consumer...")
          (let [effect-chan (or effect-chan (a/chan 100))
                stop-chan   (a/chan)
                this        (assoc this
                                   :effect-chan   effect-chan
                                   :stop-chan     stop-chan)]
            (go-consume-event! this)
            this))))
  (stop [{:keys [stop-chan] :as this}]
    (if (nil? stop-chan)
      this
      (do (log/info "Stopping event consumer...")
          (a/close! stop-chan)
          (assoc this :stop-chan nil)))))

(defn create-event-consumer
  ([{:keys [effect-chan] :as params}]
   (s/assert ::event-consumer-params params)
   (map->EventConsumer {:effect-chan effect-chan}))
  ([]
   (create-event-consumer {})))

;; --------| effect executor |---------

(defn- go-execute-effect!
  [{:keys [event-consumer event-dispatcher stop-chan] :as effect-executor}]
  (let [effect-chan (:effect-chan event-consumer)
        event-chan  (:event-chan event-dispatcher)]
    (go-loop []
      (help/when-let [chans         [effect-chan stop-chan]
                      [effect chan] (a/alts! chans :priority true)
                      continue?     (and (not= stop-chan chan) (some? effect))]
        ;; TODO: implement this
        (recur)))
    ::ok))

(defrecord EffectExecutor [event-consumer event-dispatcher stop-chan]
  c/Lifecycle
  (start [{:keys [event-consumer stop-chan] :as this}]
    (if (some? stop-chan)
      this
      (do (log/info "Starting effect executor...")
          (let [stop-chan       (a/chan)
                this            (assoc this :stop-chan stop-chan)]
            (go-execute-effect! this)
            this))))
  (stop [{:keys [event-consumer stop-chan] :as this}]
    (if (nil? stop-chan)
      this
      (do (log/info "Stopping effect executor...")
          (a/close! stop-chan)
          (assoc this :stop-chan nil)))))

(defn create-effect-executor
  []
  (map->EffectExecutor {}))

;; --------| channel pipeliner |---------

(defn- pipeline!
  [{:keys [parallelism to xform-fn from ex-handler] :as this}]
  (let [parallelism (or parallelism 1)
        to-chan     (asp/sink-chan to)
        xform       (xform-fn this)
        from-chan   (asp/source-chan from)
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

(s/def ::effect-chan help/chan?)

(s/def ::event-consumer-params (s/keys :opt-un [::effect-chan]))

(s/def ::xform-fn fn?)

(s/def ::ex-handler fn?)

(s/def ::parallelism help/pos-int?)

(s/def ::message help/nblank-str?)

(s/def ::channel-pipeliner-params (s/keys :req-un [::xform-fn ::ex-handler]
                                          :opt-un [::parallelism ::message]))
