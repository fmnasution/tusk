(ns tusk.websocket
  (:require
   [cljs.spec.alpha :as s]
   [cljs.core.async :as a]
   [com.stuartsierra.component :as c]
   [taoensso.sente :as st]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   [tusk.async.protocols :as asnc.prt]))

;; --------| websocket client |--------

(defrecord WebsocketClient [server-uri
                            client-option
                            chsk
                            recv-chan
                            send!
                            state
                            started?]
  asnc.prt/ISource
  (source-chan [websocket-client]
    (:recv-chan websocket-client))

  c/Lifecycle
  (start [{:keys [server-uri client-option started?] :as this}]
    (if started?
      this
      (do (log/info "Starting websocket client...")
          (let [{:keys [chsk ch-recv send-fn state]}
                (st/make-channel-socket! server-uri client-option)]
            (assoc this
                   :chsk      chsk
                   :recv-chan ch-recv
                   :send!     send-fn
                   :state     state
                   :started?  true)))))
  (stop [{:keys [recv-chan chsk started?] :as this}]
    (if-not started?
      this
      (do (log/info "Stopping websocket client...")
          (st/chsk-disconnect! chsk)
          (a/close! recv-chan)
          (assoc this
                 :chsk      nil
                 :recv-chan nil
                 :send!     nil
                 :state     nil
                 :started?  false)))))

(defn create-websocket-client
  [{:keys [server-uri client-option] :as params}]
  (s/assert ::websocket-params params)
  (map->WebsocketClient {:server-uri     server-uri
                         :client-option  client-option
                         :started?       false}))

;; --------| spec |---------

(s/def ::server-uri help/nblank-str?)

(s/def ::type #{:auto :ws :ajax})

(s/def ::protocol #{:http :https})

(s/def ::host help/nblank-str?)

(s/def ::params map?)

(s/def ::packer (s/or :edn #{:edn}
                      :interface #(satisfies? st.itf/IPacker %)))

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

(s/def ::websocket-params (s/keys :req-un [::server-uri]
                                  :opt-un [::client-option]))
