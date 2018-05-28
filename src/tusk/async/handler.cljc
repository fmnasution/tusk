(ns tusk.async.handler
  (:require
   [taoensso.timbre :as log]
   [tusk.async :as as]))

;; --------| event handler |---------

(as/reg-event
 :default
 (fn [_ event]
   [[:event-consumer/unknown {:event event} {:error? true}]]))

(as/reg-event
 :event-consumer/unknown
 (fn [_ [_ {:keys [event]}]]
   [[:logger/warn {:data event}]]))

(as/reg-event
 :effect-executor/unknown
 (fn [_ [_ {:keys [effect]}]]
   [[:logger/warn {:data effect}]]))

;; --------| effect handler |---------

(as/reg-effect
 :default
 (fn [_ effect]
   [:effect-executor/unknown {:effect effect} {:error? true}]))

(as/reg-effect
 :logger/info
 (fn [_ [_ {:keys [error data]}]]
   (log/info error data)))

(as/reg-effect
 :logger/warn
 (fn [_ [_ {:keys [error data]}]]
   (log/warn error data)))

(as/reg-effect
 :logger/error
 (fn [_ [_ {:keys [error data]}]]
   (log/error error data)))
