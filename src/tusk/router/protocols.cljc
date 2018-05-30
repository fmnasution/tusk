(ns tusk.router.protocols
  (:require
   [bidi.bidi :as b]
   #?@(:clj  [[clojure.spec.alpha :as s]]
       :cljs [[cljs.spec.alpha :as s]])))

;; --------| resource provider |--------

#?(:clj (defprotocol ResourceProvider
          (resources [resource-provider])))

;; --------| spec |--------

(s/def ::route-provider #(satisfies? b/RouteProvider %))

#?(:clj (s/def ::resource-provider #(satisfies? ResourceProvider %)))
