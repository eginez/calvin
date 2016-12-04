(ns eginez.calvin.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as strg]
         [cljs.nodejs :as nodejs]
         [cljs.tools.cli :refer [parse-opts]]
         [cljs.core.async :refer [put! take! chan <! >!] :as async]
         [cljs.pprint :as pprint]
         [eginez.huckleberry.core :as hb]
         [cljs.reader :as reader]))


(def fs (nodejs/require "fs"))
(def npath (nodejs/require "path"))
(def nchild (nodejs/require "child_process"))
(def nproc (nodejs/require "process"))

(defn find-file [fpath]
  (let [files (.readdirSync fs fpath)
        fname (-> (filter #(strg/includes? % "project.clj") files)
                (first))]
      (.join npath fpath fname)))


(defn samedep? [dep1 dep2]
 (and (= (:artifact dep1 ) (:artifact dep2))
      (= (:version dep1) (:version dep2))
      (= (:group dep1) (:group dep2))))

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



(defn resolve-dependencies [coordinates retrieve]
  (let [dp (hb/resolve-dependencies
                    :coordinates coordinates
                    :local-repo (:local hb/default-repos)
                    :retrieve retrieve)]
        dp))

(defn resolve-classpath [path-to-project]
  (go
    (let [options (-> (find-file path-to-project)
                      (find-lein-dependencies))
          deps (:dependencies options)
          dep-list (<!(resolve-dependencies deps true))]
    (strg/join ":" (map hb/dep->path dep-list)))))

(defn print-dep-tree [root graph depth]
  (let [art (first (filter #(samedep? root %) (keys graph)))
        deps (get graph art)]
        (println (strg/join (concat (repeat depth "*") ">")) (hb/dep->coordinate art))
        (if (not-empty deps)
          (doseq [n deps]
            (print-dep-tree n graph (inc depth))))))


(defn show-all-deps [graph]
  (when (not-empty graph)
    (let [root (dissoc (first graph) :exclusions)
          dg (second graph)
          [head-dep & _] (filter #(samedep? root %) (keys dg))]
      (do
        (println)
        (print-dep-tree head-dep dg 0)
        (recur (drop 2 graph))))))


(defn show-deps []
  (go
    (println "Calculating dependencies")
    (let [cwd (.cwd nproc)
          options (-> (find-file cwd)
                      (find-lein-dependencies))
          coordinates (:dependencies options)
          [status dep-graph dep-list] (<!(resolve-dependencies coordinates false))
          root (dissoc (first dep-graph) :exclusions)
          dg (second dep-graph)
          [head-dep & _] (filter #(samedep? root %) (keys dg))]
      (show-all-deps dep-graph))))
      ;(print-dep-tree head-dep dg 0))))

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
(def help
  (strg/join \newline (flatten ["Calvin a minimalistic build tool for clojurescript"
                       "Usage: calvin [options] args"
                       "Options:"
                       (map #(str "\t" (strg/join " " (take 2 %))) cli-options)
                       "Arguments:"
                       "\tdeps Shows dependencies"
                       "\trepl Starts a repl using either lumo or planck"])))

(defn -main[& args]
  (let [{:keys [options arguments errors summ]}
        (parse-opts args cli-options :in-order true)
        platform (:platform options)]
    (case (first arguments)
      "deps" (show-deps)
      "repl" (run-repl platform)
      nil (println help))))



(nodejs/enable-util-print!)
(set! *main-cli-fn* -main)


        


