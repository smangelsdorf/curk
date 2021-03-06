(ns curk.cmd)

(require '[lamina.core :as lam]
         '[clojure.string :as s]
         '[curk.config :as cfg]
         '[curk.state :as st])

(defn server-prefix [] (s/join [\: (cfg/server-name)]))

(defn user-prefix [{:keys [id] :as context}]
  (let [{:keys [nick user address] :as record} (st/client-record id)]
    (s/join [nick \! user \@ address])))

(defn nick-of [id]
  (let [nick (:nick (st/client-record id))]
    (if nick nick "*")))

(defn send-message
  ([target source command args] (send-message target source command args nil))
  ([{:keys [channel] :as target} source command args trail]
   (let [source-prefix (s/join [\: source])
         reply [source-prefix command]
         reply (into [] (concat reply args))
         reply (if (nil? trail) reply (conj reply (s/join [\: trail])))
         text-reply (s/join " " reply)]
     (println ">>>" text-reply)
     (lam/enqueue channel text-reply))))

(defn send-numeric
  ([context numeric args] (send-numeric context numeric args nil))
  ([{:keys [id] :as context} numeric args trail]
   (let [args (into [] (concat [(nick-of id)] args))]
     (send-message context (cfg/server-name) numeric args trail))))

(defn lusers [context args]
  (let [users (st/user-count)
        clients (st/client-count)
        max-users (st/max-user-count)
        max-clients (st/max-client-count)]
    (send-numeric context "251" [] (s/join ["There are "
                                            users " users and "
                                            (st/invisible-count) " invisible on "
                                            (st/server-count) " server(s)"]))
    (send-numeric context "252" [(str (st/ircop-count))] "IRC Operators online")
    (send-numeric context "253" [(str (st/unknown-count))] "unknown connection(s)")
    (send-numeric context "254" [(str (st/channel-count))] "channels formed")
    (send-numeric context "255" []
                  (s/join ["I have " clients " clients and "
                           (st/server-connection-count) " servers"]))
    (send-numeric context "265" [users max-clients]
                  (s/join ["Current local users " clients ", max " max-clients]))
    (send-numeric context "266" [users max-users]
                  (s/join ["Current global users " users ", max " max-users]))
    (send-numeric context "250" []
                  (s/join ["Highest connection count: " (st/max-connection-count)
                           " (" max-clients " clients) ("
                           (st/total-connection-count) " connections received)"]))))

(defn motd [context args]
  (doall (map #(send-numeric context "372" [] (s/join ["- " %])) (cfg/motd-lines)))
  (send-numeric context "376" [] "End of /MOTD command."))

(defn register [{:keys [id] :as context}]
  (let [identifier (user-prefix context)
        welcome (s/join ["Welcome to the Internet Relay Network " identifier])
        server-id (s/join ["Your host is " (cfg/server-name)
                           " running " (cfg/version)])
        start-time (s/join ["This server was created " (cfg/startup-time)])
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
                  "CHANNELLEN=50"]]
    (send-numeric context "001" [] welcome)
    (send-numeric context "002" [] server-id)
    (send-numeric context "003" [] start-time)
    (send-numeric context "004" server-info)
    (send-numeric context "005" features "are supported by this server")
    (lusers context ["LUSERS"])
    (motd context ["MOTD"])
    (st/update-client id [:registered] (fn [_] true))))

(defn check-registered [{:keys [id channel] :as context}]
  (let [{:keys [nick user address registered] :as record} (st/client-record id)]
    (if (and nick user address (not registered))
      (register context))))

(defn user [{:keys [id] :as context} [c user host server real]]
  (st/update-client id [:user] (fn [_] user))
  (st/update-client id [:real] (fn [_] real))
  (check-registered context)
  (println "User:" user))

(defn nick [{:keys [id] :as context} [c nick]]
  (st/update-client id [:nick] (fn [_] nick))
  (check-registered context)
  (println "Nick:" nick))

(defn quit [context [c message]]
  (println "Quit:" message))

(defn ping [{:keys [channel] :as context} [c tag]]
  (send-message context (cfg/server-name) "PONG" [(cfg/server-name)] tag))

(defn mode-numeric [context chan]
  (let [record (st/channel-record chan)
        modes (:modes record)
        mode-string (s/join (cons \+ modes))]
    (send-numeric context "324" [chan] mode-string)
    (send-numeric context "329" [chan (str (:created record))])))

(defn mode [{:keys [id] :as context} [c chan modes & args]]
  (let [record (st/channel-record chan)]
    (if modes
      (println modes args)
      (mode-numeric context chan))))

(defn mode-char [modes]
  (if (contains? modes \o) \@
    (if (contains? modes \v) \+
      "")))

(defn names [context [c chan]]
  (let [record (st/channel-record chan)]
    (if (nil? record)
      (send-numeric context "403" [chan] "No such channel")
      (let [memberships (:members record)
            membership-string-parts (map (juxt (comp mode-char :modes)
                                               (comp nick-of :id))
                                         memberships)
            members (s/join " " (map s/join membership-string-parts))]
        (send-numeric context "353" ["=" chan] members)
        (send-numeric context "366" [] "End of /NAMES list.")))))

(defn send-who-response [context chan membership]
  (let [{:keys [nick user address server
                away hops real]} (st/user-record (:id membership))
        status (s/join [(if away \G \H)
                        (mode-char (:modes membership))])]
    (send-numeric context "352"
                  [chan user address server nick status]
                  (s/join " " [hops real]))))

(defn who-chan [context [c chan]]
  (let [record (st/channel-record chan)]
    (if record (doall (map
                        #(send-who-response context chan %)
                        (:members record))))))

(defn who [context [c match]]
  (if (= \# (first match))
    (who-chan context [c match]))
  (send-numeric context "315" [] "End of /WHO list."))

(defn do-users [chan f]
  (let [{:keys [members] :as record} (st/channel-record chan)]
    (doseq [m members]
      (let [id (:id m)
            user (st/user-record id)]
        (f record user id)))))

(defn topic [context [c chan text]]
  (let [record (st/channel-record chan)
        current (:topic record)]
    (if text
      (let [prefix (user-prefix context)]
        (send-message context prefix "TOPIC" [chan] text)
        (st/update-channel chan [:topic] (fn [_] {:text text
                                                  :time (st/unix-now)
                                                  :by prefix})))
      (if current
        (let [{:keys [text time by]} current]
          (send-numeric context "332" [chan] text)
          (send-numeric context "333" [chan by time]))
        (send-numeric context "331" [chan] "No topic is set")))))

(defn join [{:keys [id] :as context} [c chan]]
  (let [prefix (user-prefix context)]
    (st/join-channel id chan)
    (do-users chan (fn [_ u _] (send-message u prefix "JOIN" [chan])))
    (topic context ["TOPIC" chan])
    (mode-numeric context chan)
    (names context ["NAMES" chan])
    (println prefix "Join:" chan)))

(defn part [{:keys [id] :as context} [c chan reason]]
  (let [prefix (user-prefix context)]
    (do-users chan (fn [_ u _] (send-message u prefix "PART" [chan] reason)))
    (st/part-channel id chan)))

(defn terminate [{:keys [id channels] :as context} message]
  (let [user (st/user-record id)
        prefix (user-prefix context)]
    (doseq [chan (:channels user)]
      (st/part-channel id chan)
      (do-users chan (fn [_ u _] (send-message u prefix "QUIT" [] message))))))

(defn quit [{:keys [id] :as context} [c message]]
  (terminate context (s/join ["Quit: " message]))
  (println "Quit:" id message))

(defn privmsg-chan [context chan message]
  (let [prefix (user-prefix context)]
    (do-users chan (fn [_ u id] (if (not= (:id context) id)
                                 (send-message u prefix "PRIVMSG"
                                               [chan] message))))))

(defn privmsg [context [c dest message]]
  (if (= \# (first dest))
    (privmsg-chan context dest message)))

(defn unknown-command [context [c & args]]
  (println "Got unknown command:" c))

(defn handler-for [command]
  (condp = (clojure.string/upper-case (first command))
    "USER" user
    "NICK" nick
    "QUIT" quit
    "LUSERS" lusers
    "MOTD" motd
    "PING" ping
    "JOIN" join
    "PART" part
    "QUIT" quit
    "MODE" mode
    "PRIVMSG" privmsg
    "NAMES" names
    "WHO" who
    "TOPIC" topic
    unknown-command))
