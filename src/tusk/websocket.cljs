(ns tusk.websocket
  (:require
   [cljs.spec.alpha :as s]
   [cljs.core.async :as a]
   [com.stuartsierra.component :as c]
   [taoensso.sente :as st]
   [taoensso.sente.interfaces :as sti]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   [tusk.async.protocols :as asp]
   [tusk.async :as as]))

;; --------| websocket client |--------

(defn- wrap-reply-data
  [reply-data]
  {:type :reply
   :data reply-data})

(defn- wrap-remote-event
  [remote-event]
  {:type :remote-event
   :data remote-event})

(defn- bootstrap-websocket-client!
  [{:keys [server-uri
           client-option
           chsk
           recv-chan
           send!
           state
           reply-chan
           output-chan]
    :as   websocket-client}]
  (if (every? nil? [chsk recv-chan send! state])
    (let [{:keys [chsk ch-recv send-fn state]}
          (st/make-channel-socket! server-uri client-option)]
      (a/pipeline 1 output-chan (map wrap-reply-data) reply-chan)
      (a/pipeline 1 output-chan (map wrap-remote-event) ch-recv)
      (assoc websocket-client
             :chsk      chsk
             :recv-chan ch-recv
             :send!     send-fn
             :state     state))
    websocket-client))

(defrecord WebsocketClient [server-uri
                            client-option
                            chsk
                            recv-chan
                            send!
                            state
                            reply-chan
                            output-chan
                            started?]
  asp/ISource
  (source-chan [websocket-client]
    (:output-chan websocket-client))

  c/Lifecycle
  (start [{:keys [server-uri client-option reply-chan output-chan started?]
           :as   this}]
    (if started?
      this
      (do (log/info "Starting websocket client...")
          (let [reply-chan  (or reply-chan (a/chan 100))
                output-chan (or output-chan (a/chan 100))]
            (-> this
                (assoc :reply-chan  reply-chan
                       :output-chan output-chan)
                (bootstrap-websocket-client!)
                (assoc :started? true))))))
  (stop [{:keys [chsk started?] :as this}]
    (if-not started?
      this
      (do (log/info "Stopping websocket client...")
          (st/chsk-disconnect! chsk)
          (assoc this :started? false)))))

(defn create-websocket-client
  [{:keys [server-uri client-option reply-chan output-chan]
    :as   params}]
  (s/assert ::websocket-params params)
  (map->WebsocketClient {:server-uri    server-uri
                         :client-option client-option
                         :reply-chan    reply-chan
                         :output-chan   output-chan
                         :started?      false}))

;; --------| websocket event pipeliner |---------

(defn- remote-event->local-event
  [{:keys [event] :as remote-event}]
  (let [[id data] event]
    [id {::?data data}]))

(defn- reply->event
  [{:keys [reply event] :as reply-data}]
  (let [[id data] event]
    [id (assoc data ::?data reply)]))

(defn- process-message
  [{message-type :type
    data         :data}]
  (case message-type
    :reply        (reply->event data)
    :remote-event (remote-event->local-event data)))

(defn create-websocket-client-pipeliner
  ([params]
   (let [xform-fn   (constantly (map process-message))
         ex-handler (fn [error]
                      [:websocket-client-pipeliner/error
                       {:error error}
                       {:error? true}])]
     (-> params
         (assoc :xform-fn   xform-fn
                :ex-handler ex-handler
                :message    "Pipelining remote event...")
         (as/create-channel-pipeliner))))
  ([]
   (create-websocket-client-pipeliner {})))

(defn csrf-token
  [{:keys [state] :as websocket-client}]
  (:csrf-token @state))

;; --------| spec |---------

(s/def ::server-uri help/nblank-str?)

(s/def ::type #{:auto :ws :ajax})

(s/def ::protocol #{:http :https})

(s/def ::host help/nblank-str?)

(s/def ::params map?)

(s/def ::packer (s/or :edn       #{:edn}
                      :interface #(satisfies? sti/IPacker %)))

(s/def ::ajax-opts any?)

(s/def ::wrap-recv-evs? boolean?)

(s/def ::ws-kalive-ms help/pos-int?)

(s/def ::client-option (s/keys :opt-un [::type
                                        ::protocol
                                        ::host
                                        ::params
                                        ::packer
                                        ::ajax-opts
                                        ::wrap-recv-evs?
                                        ::ws-kalive-ms]))

(s/def ::reply-chan help/chan?)

(s/def ::output-chan help/chan?)

(s/def ::websocket-params (s/keys :req-un [::server-uri]
                                  :opt-un [::client-option
                                           ::reply-chan
                                           ::output-chan]))
