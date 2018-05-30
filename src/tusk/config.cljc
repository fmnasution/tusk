(ns tusk.config
  (:require
   [com.stuartsierra.component :as c]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]
   #?@(:clj  [[clojure.spec.alpha :as s]
              [aero.core :refer [read-config]]]
       :cljs [[cljs.spec.alpha :as s]])))

;; --------| config |--------

(defrecord Config [source option value]
  c/Lifecycle
  (start [{:keys [source option value] :as this}]
    (if (some? value)
      this
      (do (log/info "Reading config...")
          (let [value #?(:clj  (read-config source option)
                         :cljs {:datastore {:kind :datascript}})]
            (assoc this :value value)))))
  (stop [{:keys [value] :as this}]
    (if (nil? value)
      this
      (do (log/info "Forgetting config...")
          (assoc this :value nil)))))

(defn create-config
  [{:keys [source option] :as params}]
  (s/assert ::config-params params)
  (map->Config {:source source
                :option option}))

;; --------| spec |--------

#?(:clj (s/def ::source help/nblank-str?))

#?(:clj (s/def ::profile #{:dev}))

#?(:clj (s/def ::option (s/keys :req-un [::profile])))

(s/def ::config-params #?(:clj  (s/keys :req-un [::source ::option])
                          :cljs map?))
