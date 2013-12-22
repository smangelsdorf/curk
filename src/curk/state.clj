(ns curk.state)

(use 'curk.state.internal)

(require 'clj-time.core
         'clj-time.format
         'clj-time.local
         'clj-time.coerce)

(def date-format (clj-time.format/formatters :mysql))

(defn now [] (clj-time.format/unparse date-format (clj-time.local/local-now)))

(defn thread-id []
  (let [thr (Thread/currentThread)]
    (clojure.string/join [\[ (.getId thr) \: (.getName thr) \]])))

(defn log [& message-parts]
  (apply println (now) (thread-id) message-parts))

(defn store-client [id content]
  (dosync (alter clients assoc id content)))

(defn update-client [id k v]
  (dosync (alter clients update-in (cons id k) v)))

(defn new-channel [chan]
  (let [now (do (int (/ (clj-time.coerce/to-long (clj-time.local/local-now)) 1000)))]
    {:name chan
     :topic nil
     :modes #{\n \t}
     :members #{}
     :created now}))

(defn new-channel-member [id record]
  {:id id
   :modes (if (empty? (:members record)) #{\o} #{})})

(defn join-channel [id chan]
  (dosync
    (alter channels update-in [chan]
           (fn [record]
             (let [record (if record record (new-channel chan))]
               (update-in record [:members]
                          #(conj % (new-channel-member id record))))))))

(defn part-channel [id chan]
  (dosync
    (alter channels update-in [chan :members]
           (fn [members] (filter #(not= id (:id %)) members)))
    (alter channels update-in [chan]
           (fn [record] (if (empty? (:members record)) nil record)))))

(def channel-record
  #(get @channels %))

(def client-record
  #(get @clients %))

(def user-record client-record)

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

(defn total-connection-count [] @client-id)

(defn new-client-id [client-info]
  (dosync (alter client-id inc)))
