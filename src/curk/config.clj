(ns curk.config)

(require 'clj-time.local)

(defn port [] 6667)

(defn server-name [] "irc.example.net")

(defn version [] "curk-0.1")

(def time-started (clj-time.local/local-now))

(defn startup-time [] time-started)

(defn motd-lines []
  ["                 _               _    "
   "                | |             | |   "
   "  ___ _   _ _ __| | ___ __   ___| |_  "
   " / __| | | | '__| |/ / '_ \\ / _ \\ __| "
   "| (__| |_| | |  |   <| | | |  __/ |_  "
   " \\___|\\__,_|_|  |_|\\_\\_| |_|\\___|\\__| "
   "                                      "
   "                                      "
   "***********************************"
   "Welcome to the curknet IRC Network."
   "***********************************"])
