(ns tusk.websocket.resources
  (:require
   [ring.util.http-response :as res]
   [taoensso.encore :as help]
   [tusk.websocket :as ws]))

;; --------| ring resource |--------

(defn ring-resource
  [{:keys [component request-method] :as request}]
  (help/cond
    :let [websocket-server (:websocket-server component)]

    (nil? websocket-server)
    (res/content-type (res/service-unavailable) "text/plain")

    :let [resource (ws/ring-resource websocket-server request-method)]

    (nil? resource)
    (res/content-type (res/method-not-allowed) "text/plain")

    :else (resource request)))
