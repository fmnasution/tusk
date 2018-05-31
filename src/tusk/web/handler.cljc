(ns tusk.web.handler
  (:require
   [tusk.async :as as]
   #?@(:cljs [[tusk.web.ajax :as wa]])))

;; --------| effect handler |---------

#?(:cljs (as/reg-effect
          :server-ajax-caller/request
          (fn [{:keys [server-ajax-caller]}
              [_ {:keys [handler route-params request-method option]
                  :or   {route-params   {}
                         request-method :get}}]]
            (wa/request! server-ajax-caller
                         handler
                         route-params
                         request-method
                         option))))
