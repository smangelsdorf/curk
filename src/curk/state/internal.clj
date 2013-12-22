(ns curk.state.internal)

(def client-id (ref 0))

(def clients (ref {}))

(def channels (ref {}))
