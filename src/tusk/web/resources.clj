(ns tusk.web.resources
  (:require
   [clojure.java.io :as io]
   [bidi.bidi :as b]
   [ring.util.http-response :as res]
   [hiccup.core :as h]
   [hiccup.page :as hp]
   [taoensso.encore :as help]))

;; --------| index resource |--------

(defn- asset-path
  [{{:keys [ring-router]} :component} path]
  (let [routes ["" (b/routes ring-router)]]
    (str (b/path-for routes :tusk.web.routes/asset) "/" path)))

(defn index-template
  [request]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name    "viewport"
            :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
    (hp/include-css "https://fonts.googleapis.com/css?family=Roboto:300,400,500")
    (hp/include-css "https://fonts.googleapis.com/icon?family=Material+Icons")
    [:title "tusk"]]
   [:body
    [:div#app]
    (hp/include-js (asset-path request "tusk/app.js"))]])

(defn index-resource
  [{:keys [request-method] :as request}]
  (if (not= :get request-method)
    (res/content-type (res/method-not-allowed) "text/plain")
    (-> (h/html (index-template request))
        (res/ok)
        (res/content-type "text/html"))))

;; --------| asset resource |--------

(defn asset-resource
  [{:keys [request-method uri] :as request}]
  (help/cond
    (not= :get request-method)
    (res/content-type (res/method-not-allowed) "text/plain")

    :let [file (io/file (subs uri 1))]

    (and (.exists file) (.isFile file))
    (res/ok file)

    :else (res/not-found)))
