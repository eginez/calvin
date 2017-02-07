(ns eginez.calvin.io
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [eginez.calvin.macros :refer [try-true]])
  (:require [clojure.string :as strg]
         [cljs.nodejs :as nodejs]
         [cljs.core.async :refer [put! take! chan <! >!] :as async]
         [cljs.pprint :as pprint]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def sep (.-sep path))

(defn readFile
   ([pathstr] (readFile pathstr "utf8"))
   ([pathstr enc] (.readFileSync fs pathstr enc)))

(defn writeFile
  [pathstr content opts]
  (.writeFileSync fs pathstr content
                #js{"flag"     (or (:flags opts) (if (:append opts) "a" "w"))
                    "mode"     (or (:mode opts)  438)
                    "encoding" (or (:encoding opts) "utf8")}))

(defn existsFile? [pathstr]
  (assert (string? pathstr))
  (try-true (.accessSync fs pathstr (.-F_OK fs))))

(defn dir?
  [pathstring]
  (assert (string? pathstring))
  (let [stats (try (.statSync fs pathstring) (catch js/Error e false))]
    (if-not stats
      false
      (.isDirectory stats))))

(defn readdir
  [dirpath]
  (assert (string? dirpath))
  (vec (.readdirSync fs dirpath)))

(defn basename
  ([p] (.basename path p))
  ([p ext] (.basename path p ext)))

(defn dirname [pathstring]
  (.dirname path pathstring))

(defn extension [pathstring]
  (.extname path pathstring))
