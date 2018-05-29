(ns tusk.web.resources
  (:require
   [ring.util.http-response :as res]))

;; --------| index resource |--------

(defn index-resource
  [_]
  (res/content-type (res/ok "Hello World!") "text/html"))
