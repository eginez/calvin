(ns eginez.calvin.io
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as strg]
            [cljs.nodejs :as nodejs]
            [cljs-node-io.fs :as io]
            [cljs-node-io.protocols :refer [IFile]]
            [cljs.core.async :refer [put! take! chan <! >!] :as async]
            [cljs.pprint :as pprint]))

(def fs (nodejs/require "fs"))
(def path (nodejs/require "path"))
(def sep (.-sep path))
(def jszip (nodejs/require "jszip"))

(deftype ZipFile [pathstring]
  Object
  (entries [_] (.load (jszip.) (io/readFile pathstring "binary"))))

