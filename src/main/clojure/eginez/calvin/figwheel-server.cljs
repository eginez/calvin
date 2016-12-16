(ns eginez.calvin.figwheel-server
  (:require [cljs.nodejs :as nodejs]
            [eginez.calvin.core :as calvin]))

(nodejs/enable-util-print!)

(defn -main []
  (println "booting up figwheel-server"))

(set! *main-cli-fn* -main)
