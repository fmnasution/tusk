(ns tusk.web.middleware
  (:require
   [clojure.string :as string]
   [ring.middleware.defaults :as mdf]
   [muuntaja.middleware :as mmtj]
   [tusk.middleware.protocols :as mp]))

;; --------| web middleware |--------

(defn- wrap-trailing-slash
  [handler]
  (fn [{:keys [uri] :as request}]
    (handler (assoc request :uri (if (and (not= "/" uri)
                                          (string/ends-with? uri "/"))
                                   (subs uri 0 (dec (count uri)))
                                   uri)))))

(defn- wrap-component
  [handler component]
  (fn [request]
    (assoc request :component component)))

(defrecord WebMiddlewareContainer []
  mp/MiddlewareContainer
  (id [_]
    ::v1)
  (middleware [_]
    [wrap-trailing-slash
     [mdf/wrap-defaults mdf/site-defaults]
     mmtj/wrap-format
     [wrap-component :component]])
  (requires [_]
    []))

(defn create-web-middleware-container
  []
  (map->WebMiddlewareContainer {}))
