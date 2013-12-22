(ns curk.core)

(use 'lamina.core
     'aleph.tcp
     '[gloss.core :only (string)])

(require '[curk.config :as cfg]
         '[curk.state :as st]
         '[curk.cmd :as cmd])

(defn strip-prefix [s]
  (let [s (clojure.string/trim s)]
    (if (= \: (first s))
      (second (split s #" " 2))
      s)))

(defn split-command [message]
  (loop [p [] s message]
    (if (= \: (first s))
      (conj p (clojure.string/join (rest s)))
      (let [spl (clojure.string/split s #" +" 2)
            newp (conj p (first spl))]
        (if (empty? (second spl))
          newp
          (recur newp (second spl)))))))

(defn message-handler [channel client-info context message]
  (let [message (strip-prefix message)
        command (split-command message)
        handler (cmd/handler-for command)]
    (st/log (get context :id) message)
    (handler context command)))

(defn client-handler [channel client-info]
  (let [id (st/new-client-id client-info)
        context {:id id
                 :channel channel
                 :client-info client-info}]
    (st/store-client id (select-keys client-info [:address]))
    (receive-all channel #(message-handler channel client-info context %))))

(defn start [& args]
  (start-tcp-server client-handler {:port (cfg/port)
                                    :frame (string :utf-8 :delimiters ["\r\n" "\n"])}))
