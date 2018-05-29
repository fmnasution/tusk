(ns tusk.middleware.protocols)

(defprotocol MiddlewareContainer
  (id [middleware-container])
  (middleware [middleware-container])
  (requires [middleware-container]))
