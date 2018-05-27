(ns tusk.websocket
  (:require
   [clojure.spec.alpha :as s]
   [clojure.core.async :as a]
   [com.stuartsierra.component :as c]
   [taoensso.sente :as st]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]))

;; --------| websocket server |--------

(defrecord WebsocketServer [server-adapter
                            server-option
                            ring-ajax-get
                            ring-ajax-post
                            recv-chan
                            send!
                            connected-uids
                            started?]
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
  (stop [{:keys [recv-chan started?] :as this}]
    (if-not started?
      this
      (do (log/info "Stopping websocket server...")
          (a/close! recv-chan)
          (assoc this
                 :ring-ajax-get  nil
                 :ring-ajax-post nil
                 :recv-chan      nil
                 :send!          nil
                 :connected-uids nil
                 :started?       false)))))

(defn create-websocket-server
  [{:keys [server-adapter server-option] :as params}]
  (s/assert ::websocket-params params)
  (map->WebsocketServer {:server-adapter server-adapter
                         :server-option  server-option
                         :started?       false}))

;; --------| spec |---------

(s/def ::server-adapter
  #(satisfies? taoensso.sente.interfaces/IServerChanAdapter %))

(s/def ::user-id-fn fn?)

(s/def ::csrf-token-fn fn?)

(s/def ::handshake-data-fn fn?)

(s/def ::ws-kalive-ms help/pos-int?)

(s/def ::lp-timeout-ms help/pos-int?)

(s/def ::send-buf-ms-ajax help/pos-int?)

(s/def ::send-buf-ms-ws help/pos-int?)

(s/def ::packer
  (s/or :edn #{:edn}
        :interface #(satisfies? taoensso.sente.interfaces/IPacker %)))

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
