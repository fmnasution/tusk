(ns tusk.async.protocols)

(defprotocol ISource
  (source-chan [source]))

(defprotocol ISink
  (sink-chan [sink]))
