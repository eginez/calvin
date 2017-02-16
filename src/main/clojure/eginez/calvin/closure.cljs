(ns eginez.calvin.closure)
  ;(:require-macros [cljs.core.async.macros :refer [go]]
  ;  [eginez.calvin.macros :refer [if-let*]])
  ;(:require [clojure.string :as strg]
   ; [cljs.core.async :refer [put! take! chan <! >!] :as async]
   ; [cljs.pprint :as pprint]
   ; [cljs.nodejs :as nodejs])

(def closure-compile (.gulp (js/require "google-closure-compiler-js")))





