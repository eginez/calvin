#!/usr/bin/env lumo

(require-macros '[cljs.core.async.macros :refer [go]])
(require '[clojure.string :as strg]
         '[cljs.nodejs :as nodejs]
         '[cljs.core.async :refer [put! take! chan <! >!] :as async] 
         '[eginez.huckleberry.core :as hb]
         '[cljs.reader :as reader])


(def fs (nodejs/require "fs"))
(def npath (nodejs/require "path"))
(def nproc (nodejs/require "process"))
(def argv (.-argv nproc))

(defn find-file [fpath]
  (let [files (.readdirSync fs fpath)
        fname (-> (filter #(strg/includes? % "project.clj") files)
                (first))]
      (.join npath fpath fname)))


(defn load-content [file]
  (-> (.readFileSync fs file) .toString))

(defn find-lein-dependencies [lein-project-file]
  (let [content (load-content lein-project-file)
        rcontent (reader/read-string content)
        [_ name version & opts] rcontent
        lopts (partition 2 opts)
        mapopts (map #(assoc {} (first %) (second %)) lopts)
        ret (reduce merge mapopts)]
      ret))


(defn main [path retrieve]
  (let [options (-> (find-file path)
                    (find-lein-dependencies))
        deps (:dependencies options)]
    (do 
      (go (let [dp (<!(hb/resolve-dependencies 
                                      :coordinates deps
                                      :local-repo (.resolve npath "./tmp/repo/")
                                      :retrieve retrieve))]

            (if retrieve
              (println "classpath is: " (strg/join ":" (map hb/dep->path dp)))
              (println "dependency graph " (second dp))))))))


;Pass the a directory with a project file in it and it'll fetch the dependencies
(let [pth (drop 6 argv)
      rslv (.resolve npath (first pth))]
  (main rslv (= "true" (strg/lower-case (last pth)))))


        


