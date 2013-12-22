(ns curk.state)

(require 'clj-time.core
         'clj-time.format
         'clj-time.local)

(def date-format (clj-time.format/formatters :mysql))

(defn now [] (clj-time.format/unparse date-format (clj-time.local/local-now)))

(defn thread-id []
  (let [thr (Thread/currentThread)]
    (clojure.string/join [\[ (.getId thr) \: (.getName thr) \]])))

(defn log [& message-parts]
  (apply println (now) (thread-id) message-parts))

(def clients (ref {}))

(defn store-client [id content]
  (dosync (alter clients assoc id content)))

(defn update-client [id k v]
  (dosync (alter clients update-in (cons id k) v)))

(def channels (ref {}))

(defn new-channel [chan]
  #{:name chan
    :topic ""
    :modes #{\n \t}
    :members #{}})

(defn new-channel-member [id record]
  #{:id id
    :modes (if (empty? (:members record)) #{\o} #{})})

(defn join-channel [id chan]
  (dosync
    (alter channels update-in [chan]
           #(if (not %) (new-channel chan)))
    (alter channels update-in [chan :members]
           #(conj % (new-channel-member id %)))))

(def client-record
  #(get @clients %))

(defn client-count [] (count @clients))

(defn user-count [] (client-count))

(defn invisible-count [] (client-count))

(defn ircop-count [] 0)

(defn unknown-count [] 0)

(defn server-count [] 1)

(defn max-client-count [] (client-count))

(defn max-user-count [] (client-count))

(defn channel-count [] (count @channels))

(defn server-connection-count [] 0)

(defn max-connection-count [] (client-count))

(def client-id (ref 0))

(defn new-client-id [client-info]
  (dosync (alter client-id inc)))
