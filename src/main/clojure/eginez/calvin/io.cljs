(ns eginez.calvin.io
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [eginez.calvin.macros :refer [if-let*]])
  (:require [clojure.string :as strg]
            [cljs.nodejs :as nodejs]
            [cljs-node-io.fs :as io]
            [cljs-node-io.file :refer [File]]
            [cljs-node-io.protocols :refer [IFile]]
            [cljs.core.async :refer [put! take! chan <! >!] :as async]
            [cljs.pprint :as pprint]
            [eginez.calvin.core :refer [sourcepath]])
  (:import goog.Uri))

(def fs (nodejs/require "fs"))
(def path (nodejs/require "path"))
(def sep (.-sep path))
(def jszip (nodejs/require "jszip"))

(defmulti load-zip type)
(defmethod load-zip js/String [pathstr] (.load (jszip.) (io/readFile pathstr "binary")))
(defmethod load-zip File [file]
  (do
    (println "loading file " (.getName file))
    (load-zip (.getAbsolutePath file))))

(defn all-classpath-urls []
  (let [cl (strg/split @sourcepath #":")]
    (->> cl (take-while identity))))


(deftype ZipFile [zipobject]
  Object
  (entries [_] (load-zip zipobject)))


(defn line-seq
  "Returns the lines of text from rdr as a lazy sequence of strings."
  [rdr]
  (if-let* [content (.read rdr)
             lines (strg/split-lines content)]
    (lazy-seq lines)))

(defn resource [res]
  (if-let* [pred (fn [jar-or-dir]
                   (cond
                     (and
                       (io/dir? jar-or-dir)
                       (io/fexists? (.join path jar-or-dir res)))
                     (Uri. (.join path jar-or-dir res))

                     (and
                       (.endsWith jar-or-dir "jar")
                       (.file (load-zip jar-or-dir) res))
                     (Uri. (str "jar:" jar-or-dir "!/" res))))
            container (some pred (all-classpath-urls))]
    container))





