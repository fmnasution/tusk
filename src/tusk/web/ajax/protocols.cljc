(ns tusk.web.ajax.protocols)

(defprotocol IAjaxCaller
  (process-option [ajax-caller option]))
