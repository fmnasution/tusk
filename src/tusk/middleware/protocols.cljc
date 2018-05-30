(ns tusk.middleware.protocols
  (:require
   #?@(:clj  [[clojure.spec.alpha :as s]]
       :cljs [[cljs.spec.alpha :as s]])))

;; --------| middleware container |--------

(defprotocol MiddlewareContainer
  (id [middleware-container])
  (middleware [middleware-container])
  (requires [middleware-container]))

;; --------| spec |--------

(s/def ::middleware-container #(satisfies? MiddlewareContainer %))
