(ns tusk.web.ajax.impl
  (:require
   [bidi.bidi :as b]
   [ajax.core :as aj]
   [taoensso.encore :as help]
   [tusk.web.ajax.protocols :as wap]
   #?@(:clj  [[clojure.core.async :as a]]
       :cljs [[cljs.core.async :as a]])))

;; --------| ajax |--------

(defn- request!
  [route-provider handler route-params request-method option]
  (let [routes     ["" (b/routes route-provider)]
        uri        (apply b/path-for
                          routes
                          handler
                          (flatten (seq route-params)))
        request-fn (case request-method
                     :get    aj/GET
                     :post   aj/POST
                     :put    aj/PUT
                     :delete aj/DELETE)]
    (request-fn uri option)))

(defn- process-option
  [response-chan {:keys [handler error-handler event error-event] :as option}]
  (let [callback      (fn [response-chan event]
                        (fn [response]
                          (let [data {:response response
                                      :event    event}]
                            (a/put! response-chan data))))
        handler       (callback response-chan event)
        error-handler (callback response-chan error-event)]
    (-> option
        (dissoc :event :error-event)
        (assoc :handler handler :error-handler error-handler))))

(defn create-requester
  [{:keys [response-chan] :as ajax-caller}]
  (fn [handler route-params request-method option]
    (request! ajax-caller
              handler
              route-params
              request-method
              (->> option
                   (process-option response-chan)
                   (wap/process-option ajax-caller)))))

(defn add-interceptors
  [option interceptors]
  (help/update-in option [:interceptors] [] #(into % interceptors)))
