(ns eginez.calvin.tasks
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [eginez.calvin.macros :refer [if-let*]])
  (:require [clojure.string :as strg]
         [cljs.core.async :refer [put! take! chan <! >!] :as async]
         [cljs.pprint :as pprint]
         [cljs.js :as cljs]
         [cljs.reader :as reader]
         [eginez.huckleberry.core :as hb]
         [eginez.calvin.io :as io]))

(def empty-state (cljs/empty-state))
(def COMPILERS-EXTENSIONS #{:cljs :cljc :cljs.cache.edn :js :clj})
(def LANG-EXTENSIONS {"cljs" :clj "clj" :clj "js" :js "cljs.cache.edn" nil})

(defn default-build-handler [{:keys [error value]}]
  (if error
    (println error)
    (do
      (print value)
      (io/writeFile "/Users/eginez/repos/calvin/egz.js" value nil))))

(defn get-lang-from-file-name [file-name]
  (let [ext (second (strg/split file-name #"\." 2))]
    (get LANG-EXTENSIONS ext)))

(defn choose-best-src-file [src-coll]
  (first src-coll))

(defn find-src-in-dir [dir libname libpath]
  (let [dir-path (io/dirname (str dir io/sep libpath))
        file-name (io/basename libpath)
        all-files (io/readdir dir-path)
        all-src-files (filter #(and
                                  (contains? COMPILERS-EXTENSIONS (keyword (second (strg/split % #"\." 2))))
                                  (= file-name (first (strg/split % #"\."))))
                              all-files)
        src-file (str dir-path io/sep (choose-best-src-file all-src-files))]

    (println "Found " src-file)
    [src-file (io/readFile src-file) (get-lang-from-file-name src-file)]))

(defn find-file-in-container [container libname libpath]
  (cond
    (re-matches #"^goog/.*" libpath) [libpath "" :js]
    (re-matches #"^clojure/.*" libpath) [libpath "" :clj]))
    ;(io/dir? container) (find-src-in-dir container libname libpath)))


(defn create-load-fn-with-classpath [classpath]
  (fn [{:keys [name macros path file] :as m} cb]
    (do
      (println "looking for " name "and " path " in " file)
      (let [[file-path file-src lang] (find-file-in-container classpath name path)]
        (cb {:lang lang :source file-src :file file-path})))))


(defn build [classpath file-path]
  (if-let* [valid (io/existsFile? file-path)
            content-str (io/readFile file-path)
            file-name "egz-file"]
    (cljs/compile-str
      empty-state
      content-str
      "egz-file"
      { :verbose true
        :load (create-load-fn-with-classpath classpath)}
      default-build-handler)))
