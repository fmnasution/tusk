(ns tusk.element
  (:require
   [cljs.spec.alpha :as s]
   [goog.dom :as gdom]
   [com.stuartsierra.component :as c]
   [reagent.core :as r]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]))

;; --------| element |--------

(defn index
  [message]
  [:div
   [:h1 message]])

(defrecord Element [config config-key node]
  c/Lifecycle
  (start [{:keys [config config-key node] :as this}]
    (if (some? node)
      this
      (do (log/info "Mounting element...")
          (let [config (as-> config <>
                         (get-in <> [:value config-key])
                         (s/assert ::element-config <>))
                node   (gdom/getRequiredElement (:target-id config))]
            (r/render [index "Hello World!"] node)
            (assoc this :node node)))))
  (stop [{:keys [node] :as this}]
    (if (nil? node)
      this
      (do (log/info "Unmounting element...")
          (r/unmount-component-at-node node)
          (assoc this :node nil)))))

(defn create-element
  [{:keys [config-key] :as params}]
  (s/assert ::element-params params)
  (map->Element {:config-key config-key}))

;; --------| spec |--------

(s/def ::target-id help/nblank-str?)

(s/def ::element-config (s/keys :req-un [::target-id]))

(s/def ::config-key keyword?)

(s/def ::element-params (s/keys :req-un [::config-key]))
