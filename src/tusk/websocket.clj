(ns tusk.websocket
  (:require
   [clojure.spec.alpha :as s]
   [clojure.core.async :as a]
   [com.stuartsierra.component :as c]
   [taoensso.sente :as st]
   [taoensso.sente.interfaces :as sti]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   [tusk.async.protocols :as asp]
   [tusk.async :as as]))

;; --------| websocket server |--------

(defrecord WebsocketServer [server-adapter
                            server-option
                            ring-ajax-get
                            ring-ajax-post
                            recv-chan
                            send!
                            connected-uids
                            started?]
  asp/ISource
  (source-chan [websocket-server]
    (:recv-chan websocket-server))

  c/Lifecycle
  (start [{:keys [server-adapter server-option started?] :as this}]
    (if started?
      this
      (do (log/info "Starting websocket server...")
          (let [{:keys [ch-recv
                        send-fn
                        connected-uids
                        ajax-post-fn
                        ajax-get-or-ws-handshake-fn]}
                (st/make-channel-socket! server-adapter server-option)]
            (assoc this
                   :ring-ajax-get  ajax-get-or-ws-handshake-fn
                   :ring-ajax-post ajax-post-fn
                   :recv-chan      ch-recv
                   :send!          send-fn
                   :connected-uids connected-uids
                   :started?       true)))))
  (stop [this]
    this))

(defn create-websocket-server
  [{:keys [server-adapter server-option] :as params}]
  (s/assert ::websocket-params params)
  (map->WebsocketServer {:server-adapter server-adapter
                         :server-option  server-option
                         :started?       false}))

(defn ring-resource
  [{:keys [ring-ajax-get ring-ajax-post] :as websocket-server} request-method]
  (case request-method
    :get  ring-ajax-get
    :post ring-ajax-post
    nil))

;; --------| websocket event pipeliner |---------

(defn- remote-event->local-event
  [{:keys [id ?data ring-req uid client-id ?reply-fn]
    :as   remote-event}]
  [id {::?data        ?data
       ::ring-request ring-req
       ::peer-id      uid
       ::device-id    client-id
       ::?reply-fn    ?reply-fn}])

(defn create-websocket-server-pipeliner
  ([params]
   (let [xform-fn   (constantly (map remote-event->local-event))
         ex-handler (fn [error]
                      [:websocket-server-event-pipeliner/error
                       {:error error}
                       {:error? true}])]
     (-> params
         (assoc :xform-fn   xform-fn
                :ex-handler ex-handler
                :message    "Pipelining remote event...")
         (as/create-channel-pipeliner))))
  ([]
   (create-websocket-server-pipeliner {})))

;; --------| spec |---------

(s/def ::server-adapter #(satisfies? sti/IServerChanAdapter %))

(s/def ::user-id-fn fn?)

(s/def ::csrf-token-fn fn?)

(s/def ::handshake-data-fn fn?)

(s/def ::ws-kalive-ms help/pos-int?)

(s/def ::lp-timeout-ms help/pos-int?)

(s/def ::send-buf-ms-ajax help/pos-int?)

(s/def ::send-buf-ms-ws help/pos-int?)

(s/def ::packer (s/or :edn       #{:edn}
                      :interface #(satisfies? sti/IPacker %)))

(s/def ::server-option (s/keys :opt-un [::user-id-fn
                                        ::csrf-token-fn
                                        ::handshake-data-fn
                                        ::ws-kalive-ms
                                        ::lp-timeout-ms
                                        ::send-buf-ms-ajax
                                        ::send-buf-ms-ws
                                        ::packer]))

(s/def ::websocket-params (s/keys :req-un [::server-adapter]
                                  :opt-un [::server-option]))
