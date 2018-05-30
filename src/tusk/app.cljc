(ns tusk.app
  (:require
   [com.stuartsierra.component :as c]
   [tusk.system :as st]))

#?(:cljs (defonce system
           (c/start (st/create-system-map {}))))

#?(:cljs (enable-console-print!))
