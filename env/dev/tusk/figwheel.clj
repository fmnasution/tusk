(ns tusk.figwheel
  (:require
   [clojure.spec.alpha :as s]
   [com.stuartsierra.component :as c]
   [figwheel-sidecar.system :as fws]
   [taoensso.timbre :as log]
   [taoensso.encore :as help]))

;; --------| figwheel server |--------

(defrecord FigwheelServer [config config-key figwheel started?]
  c/Lifecycle
  (start [{:keys [figwheel started?] :as this}]
    (if started?
      this
      (do (log/info "Starting figwheel server...")
          (let [config   (as-> config <>
                           (get-in <> [:value config-key])
                           (s/assert ::figwheel-server-config <>))
                figwheel (or figwheel (fws/figwheel-system config))]
            (assoc this
                   :figwheel (c/start figwheel)
                   :started? true)))))
  (stop [{:keys [figwheel started?] :as this}]
    (if-not started?
      this
      (do (log/info "Stopping figwheel server...")
          (assoc this
                 :figwheel (c/stop figwheel)
                 :started? false)))))

(defn create-figwheel-server
  [{:keys [config-key] :as params}]
  (s/assert ::figwheel-server-params params)
  (map->FigwheelServer {:config-key config-key
                        :started?   false}))

;; --------| spec |--------

(s/def ::id help/nblank-str?)

(s/def ::source-paths (s/coll-of help/nblank-str?))

(s/def ::compiler map?)

(s/def ::websocket-host (s/or :exact     help/nblank-str?
                              :non-exact #{:js-client-host
                                           :server-ip
                                           :server-hostname}))

(s/def ::on-jsload help/nblank-str?)

(s/def ::autoload boolean?)

(s/def ::heads-up-display boolean?)

(s/def ::load-warninged-code boolean?)

(s/def ::figwheel-client-option (s/keys :opt-un [::websocket-host
                                                 ::on-jsload
                                                 ::autoload
                                                 ::heads-up-display
                                                 ::load-warninged-code]))

(s/def ::figwheel (s/or :boolean boolean?
                        :config  ::figwheel-client-option))

(s/def ::build (s/keys :req-un [::id ::source-paths ::compiler ::figwheel]))

(s/def ::all-builds (s/coll-of ::build))

(s/def ::http-server-port help/pos-int?)

(s/def ::server-port help/pos-int?)

(s/def ::server-ip help/nblank-str?)

(s/def ::css-dirs (s/coll-of help/nblank-str?))

(s/def ::ring-handler help/qualified-symbol?)

(s/def ::clj boolean?)

(s/def ::cljc boolean?)

(s/def ::reload-clj-files-config (s/keys :opt-un [::clj ::cljc]))

(s/def ::reload-clj-files (s/or :boolean boolean?
                                :config  ::reload-clj-files-config))

(s/def ::open-file-command help/nblank-str?)

(s/def ::repl boolean?)

(s/def ::server-logfile help/nblank-str?)

(s/def ::nrepl-port help/pos-int?)

(s/def ::nrepl-middleware (s/coll-of help/nblank-str?))

(s/def ::watcher #{:polling})

(s/def ::hawk-options (s/keys :req-un [::watcher]))

(s/def ::load-all-builds boolean?)

(s/def ::figwheel-options (s/keys :opt-un [::http-server-port
                                           ::server-port
                                           ::server-ip
                                           ::css-dirs
                                           ::ring-handler
                                           ::reload-clj-files
                                           ::open-file-command
                                           ::repl
                                           ::server-logfile
                                           ::nrepl-port
                                           ::nrepl-middleware
                                           ::hawk-options
                                           ::load-all-builds]))

(s/def ::build-ids (s/coll-of help/nblank-str?))

(s/def ::figwheel-server-config (s/keys :req-un [::all-builds
                                                 ::figwheel-options
                                                 ::build-ids]))

(s/def ::config-key keyword?)

(s/def ::figwheel-server-params (s/keys :req-un [::config-key]))
