(ns curk.cmd)

(use 'lamina.core)

(require '[clojure.string :as s]
         '[curk.config :as cfg]
         '[curk.state :as st])

(defn server-prefix [] (s/join [\: (cfg/server-name)]))

(defn nick-of [id]
  (let [nick (:nick (st/client-record id))]
    (if nick nick "*")))

(defn send-numeric [{:keys [id channel] :as context} numeric args]
  (let [target (nick-of id)
        trail (last args)
        args (butlast args)
        reply [(server-prefix) numeric target]
        reply (if (empty? args) reply (apply conj reply args))
        reply (conj reply (if (.contains trail " ") (s/join [\: trail]) trail))]
    (enqueue channel (s/join " " reply))))

(defn lusers [context args]
  (let [users (st/user-count)
        clients (st/client-count)
        max-users (st/max-user-count)
        max-clients (st/max-client-count)]
    (send-numeric context "251" [(s/join ["There are "
                                          users " users and "
                                          (st/invisible-count) " invisible on "
                                          (st/server-count) " server(s)"])])
    (send-numeric context "252" [(str (st/ircop-count)) "IRC Operators online"])
    (send-numeric context "253" [(str (st/unknown-count)) "unknown connection(s)"])
    (send-numeric context "254" [(str (st/channel-count)) "channels formed"])
    (send-numeric context "255" [(s/join ["I have "
                                          clients " clients and "
                                          (st/server-connection-count) " servers"])])
    (send-numeric context "265" [users max-clients
                                 (s/join ["Current local users " clients
                                          ", max " max-clients])])
    (send-numeric context "266" [users max-users
                                 (s/join ["Current global users " users
                                          ", max " max-users])])
    (send-numeric context "250" [(s/join ["Highest connection count: "
                                          (st/max-connection-count) " ("
                                          max-clients " clients) ("
                                          @st/client-id " connections received)"])])))

(defn motd [context args]
  (doall (map #(send-numeric context "372" [(s/join ["- " %])]) (cfg/motd-lines)))
  (send-numeric context "376" ["End of /MOTD command."]))

(defn register [{:keys [id] :as context} identifier]
  (let [welcome [(s/join ["Welcome to the Internet Relay Network " identifier])]
        server-id [(s/join ["Your host is " (cfg/server-name)
                           " running " (cfg/version)])]
        start-time [(s/join ["This server was created " (cfg/startup-time)])]
        server-info [(cfg/server-name) (cfg/version) "i" "biklmnpst"]
        features ["CHANTYPES=#"
                  "CHANMODES=b,k,l,imnpst"
                  "CHANLIMIT=#:120"
                  "PREFIX=(ov)@+"
                  "MAXLIST=b:100"
                  "MODES=6"
                  "NETWORK=curknet"
                  "CHARSET=utf-8"
                  "NICKLEN=32"
                  "CHANNELLEN=50"
                  "are supported by this server"]]
    (send-numeric context "001" welcome)
    (send-numeric context "002" server-id)
    (send-numeric context "003" start-time)
    (send-numeric context "004" server-info)
    (send-numeric context "005" features)
    (lusers context ["LUSERS"])
    (motd context ["MOTD"])
    (st/update-client id [:registered] (fn [_] true))))

(defn check-registered [{:keys [id channel] :as context}]
  (let [{:keys [nick user address registered] :as record} (st/client-record id)]
    (if (and nick user address (not registered))
      (register context (s/join [nick \! user \@ address])))))

(defn user [{:keys [id] :as context} [c user host server real]]
  (st/update-client id [:user] (fn [_] user))
  (check-registered context)
  (println "User:" user))

(defn nick [{:keys [id] :as context} [c nick]]
  (st/update-client id [:nick] (fn [_] nick))
  (check-registered context)
  (println "Nick:" nick))

(defn quit [context [c message]]
  (println "Quit:" message))

(defn unknown-command [context [c & args]]
  (println "Got unknown command:" c))

(defn handler-for [command]
  (condp = (clojure.string/upper-case (first command))
    "USER" user
    "NICK" nick
    "QUIT" quit
    "LUSERS" lusers
    "MOTD" motd
    unknown-command))
