(ns tusk.web.ajax
  (:require
   [cljs.spec.alpha :as s]
   [cljs.core.async :as a]
   [com.stuartsierra.component :as c]
   [ajax.core :as aj]
   [bidi.bidi :as b]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   [tusk.async.protocols :as asp]
   [tusk.router.protocols :as rrp]
   [tusk.async :as as]
   [tusk.web.ajax.impl :as waip]
   [tusk.web.ajax.protocols :as wap]
   [tusk.websocket :as ws]))

;; --------| server ajax caller |--------

(defn- collect-routes
  [component]
  (into {}
        (comp
         (map val)
         (filter #(s/valid? ::rrp/route-provider %))
         (map b/routes))
        component))

(defn- csrf-token-interceptor
  [{:keys [websocket-client] :as ajax-caller}]
  (aj/to-interceptor
   {:name    "CSRF Token"
    :request (fn [request]
               (if-let [csrf-token (ws/csrf-token websocket-client)]
                 (assoc-in request [:headers :x-csrf-token] csrf-token)
                 request))}))

(defrecord ServerAjaxCaller [websocket-client routes response-chan requester]
  wap/IAjaxCaller
  (process-option [server-ajax-caller option]
    (let [-csrf-token-interceptor (csrf-token-interceptor server-ajax-caller)]
      (waip/add-interceptors option [-csrf-token-interceptor])))

  asp/ISource
  (source-chan [server-ajax-caller]
    (:response-chan server-ajax-caller))

  b/RouteProvider
  (routes [server-ajax-caller]
    (:routes server-ajax-caller))

  c/Lifecycle
  (start [{:keys [response-chan requester] :as this}]
    (if (some? requester)
      this
      (do (log/info "Starting server ajax caller...")
          (let [response-chan (or response-chan (a/chan 100))
                routes        (collect-routes this)
                this          (assoc this
                                     :routes        routes
                                     :response-chan response-chan)
                requester     (waip/create-requester this)]
            (assoc this :requester requester)))))
  (stop [{:keys [requester] :as this}]
    (if (nil? requester)
      this
      (do (log/info "Stopping server ajax caller...")
          (assoc this :routes nil :requester nil)))))

(defn create-server-ajax-caller
  ([{:keys [response-chan] :as params}]
   (s/assert ::server-ajax-caller-params params)
   (map->ServerAjaxCaller {:response-chan response-chan}))
  ([]
   (create-server-ajax-caller {})))

;; --------| server ajax pipeliner |--------

(defn- response-option->event
  [{:keys [response event] :as response-option}]
  (let [[id data] event]
    [id (assoc data ::response response)]))

(defn create-server-ajax-pipeliner
  ([params]
   (let [xform-fn   (constantly (map response-option->event))
         ex-handler (fn [error]
                      [:server-ajax-pipeliner/error
                       {:error error}
                       {:error? true}])]
     (-> params
         (assoc :xform-fn   xform-fn
                :ex-handler ex-handler
                :message    "Pipelining response from server ajax caller...")
         (as/create-channel-pipeliner))))
  ([]
   (create-server-ajax-pipeliner {})))

;; --------| spec |--------

(s/def ::response-chan help/chan?)

(s/def ::server-ajax-caller-params (s/keys :opt-un [::response-chan]))
