(ns tusk.router.protocols
  (:require
   [bidi.bidi :as b]
   [clojure.spec.alpha :as s]))

;; --------| resource provider |--------

(defprotocol ResourceProvider
  (resources [resource-provider]))

;; --------| spec |--------

(s/def ::route-provider #(satisfies? b/RouteProvider %))

(s/def ::resource-provider #(satisfies? ResourceProvider %))
