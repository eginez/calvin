#!/usr/bin/env lumo

(require-macros '[cljs.core.async.macros :refer [go]])
(require '[clojure.string :as strg]
         '[cljs.nodejs :as nodejs]
         '[cljs.tools.cli :refer [parse-opts]]
         '[cljs.core.async :refer [put! take! chan <! >!] :as async] 
         '[eginez.huckleberry.core :as hb]
         '[cljs.reader :as reader])


(def fs (nodejs/require "fs"))
(def npath (nodejs/require "path"))
(def nchild (nodejs/require "child_process"))
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


(defn resolve-classpath [path-to-project]
  (let [options (-> (find-file path-to-project)
                    (find-lein-dependencies))
        deps (:dependencies options)]
      (go (let [dp (<!(hb/resolve-dependencies 
                        :coordinates deps
                        :local-repo (.resolve npath "./tmp/repo/")
                        :retrieve true))]
            (strg/join ":" (map hb/dep->path dp))))))

(defn build-cmd-for-platform [platform classpath]
  (case platform
    "lumo"    ["lumo" ["-c" classpath]]
    "planck"  ["planck" ["-c" classpath]]))

(defn run-repl [platform]
  (go
    (let [cwd (.cwd nproc)
          classpath (<! (resolve-classpath cwd))
          [bin args] (build-cmd-for-platform platform classpath)
          proc (.spawn nchild bin (clj->js args) (clj->js {:stdio [0 1 2]}))]
      proc)))

(def cli-options [["-h" "--help"]
                  ["-p" "--platform PLATFORM" "Either planck or lumo"
                   :default "lumo"]])

;Pass the a directory with a project file in it and it'll fetch the dependencies
(let [args (drop 6 argv)
      {:keys [options arguments errors summ]} (parse-opts args cli-options :in-order true)
      platform (:platform options)]
    (case (first arguments)
      nil (run-repl platform)))


        


