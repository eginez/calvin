(ns eginez.calvin.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as strg]
            [cljs.nodejs :as nodejs]
            [cljs.tools.cli :refer [parse-opts]]
            [cljs.core.async :refer [put! take! chan <! >!] :as async]
            [cljs.pprint :as pprint]
            [cljs.analyzer :as ana]
            [eginez.huckleberry.core :as hb]
            [cljs.reader :as reader]))


(def fs (nodejs/require "fs"))
(def npath (nodejs/require "path"))
(def nchild (nodejs/require "child_process"))
(def nproc (nodejs/require "process"))
(def build-preface '(require '[lumo.build.api :as b]))

(defn find-file [fpath]
  (try
    (let [files (.readdirSync fs fpath)
          fname (-> (filter #(strg/includes? % "project.clj") files)
                    (first))]
      (or (.join npath fpath fname) nil))
    (catch js/Error e nil)))



(defn samedep? [dep1 dep2]
  (and (= (:artifact dep1 ) (:artifact dep2))
       (= (:version dep1) (:version dep2))
       (= (:group dep1) (:group dep2))))

(defn load-content [file]
  (try
    (-> (.readFileSync fs file) .toString)
    (catch js/Error e nil)))

(defn find-lein-project-configuration [lein-project-file]
  (when lein-project-file
    (let [content (load-content lein-project-file)
          rcontent (reader/read-string content)
          [_ name version & opts] rcontent
          lopts (partition 2 opts)
          mapopts (map #(assoc {} (first %) (second %)) lopts)
          ret (reduce merge mapopts)]
      ret)))

(defn resolve-dependencies [coordinates retrieve]
  (let [dp (hb/resolve-dependencies
            :coordinates coordinates
            :local-repo (:local hb/default-repos)
            :retrieve retrieve)]
    dp))

(defn with-lein-project [path & opts]
  (let [file (find-file path)
        options (find-lein-project-configuration file)]
    (get-in options (vec opts))))

(defn find-coordinates-in-project [path]
  (let [project-file (find-file path)
        options (find-lein-project-configuration project-file)
        coordiantes (:dependencies options)]
    coordiantes))

(defn find-srcs-in-project [path id]
  (let [builds (with-lein-project path :cljsbuild :builds)
        srcs (-> (filter #(= (:id %) id) builds) first)
        dropped (rest (:source-paths srcs))]
    (if (not (empty? dropped))
      (println "Current lumo api does not support multiple sources, dropping " dropped))
    (first (:source-paths srcs))))

(defn find-compiler-opts-in-project [path id]
  ;;TODO warn when target is not nodejs
  (let [builds (with-lein-project path :cljsbuild :builds)
        srcs (-> (filter #(= (:id %) id) builds) first)
        opts (:compiler srcs)
        main (:main opts)]
    (assoc opts :main `'~main)))

(defn build-build-command [src-projects compiler-options]
  (let [b `(b/build ~src-projects ~compiler-options)]
    (->> (map str [build-preface b])
         (strg/join " "))))

(defn resolve-classpath [path-to-project]
  (go
    (let [deps (find-coordinates-in-project path-to-project)]
      (when deps
        (let [dep-list (<! (resolve-dependencies deps true))]
          (strg/join ":" (map hb/dep->path dep-list)))))))


(defn print-dep-tree [root graph depth]
  (let [art (first (filter #(samedep? root %) (keys graph)))
        deps (get graph art)]
    (println (strg/join (concat (repeat depth "*") ">")) (hb/dep->coordinate art))
    (if (not-empty deps)
      (doseq [n deps]
        (print-dep-tree n graph (inc depth))))))

(defn lumo-build-cmd [path id classpath]
  (let [src-path (find-srcs-in-project path id)
        compiler-options (find-compiler-opts-in-project path id)
        build-cmd (build-build-command src-path compiler-options)
        final-cmd (str "\"" (strg/replace-all build-cmd #"\"" "\\\"") "\"")]
    ;;(println "build lumo cmd with " final-cmd " and path " classpath)
    ["lumo" ["-c" (str src-path ":" classpath) "-e" final-cmd]]))

(defn show-all-deps [graph]
  (when (not-empty graph)
    (let [root (dissoc (first graph) :exclusions)
          dg (second graph)
          [head-dep & _] (filter #(samedep? root %) (keys dg))]
      (do
        (println)
        (print-dep-tree head-dep dg 0)
        (recur (drop 2 graph))))))

(defn build-cmd-for-platform [platform classpath]
  (let [classpath-cmd (if classpath ["-c" classpath] [])]
    (case platform
      "lumo"    (conj ["lumo"] classpath-cmd)
      "planck"  (conj ["planck"] classpath-cmd))))

(defn show-deps [cwd]
  (go
    (println "Calculating dependencies")
    (if-let [coordinates (find-coordinates-in-project cwd)]
      (let [[status dep-graph dep-list] (<!(resolve-dependencies coordinates false))
            root (dissoc (first dep-graph) :exclusions)
            dg (second dep-graph)
            [head-dep & _] (filter #(samedep? root %) (keys dg))]
        (show-all-deps dep-graph))
      (println "No dependencies file found are you missing a project.clj or boot.clj?"))))
      ;;(print-dep-tree head-dep dg 0)

(defn run-build [cwd id]
  (go
    (let [classpath (<! (resolve-classpath cwd))
          [bin args] (lumo-build-cmd cwd id classpath)
          proc (.spawn nchild bin (clj->js args) (clj->js {:stdio [0 1 2] :shell true}))]
      proc)))

(defn run-repl [platform cwd rest-args]
  (go
    (let [classpath (<! (resolve-classpath cwd))
          [bin args] (build-cmd-for-platform platform classpath)
          proc (.spawn nchild bin (clj->js (concat args rest-args)) (clj->js {:stdio [0 1 2]}))]
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
                                "\tbuild [id] Builds the project using the 'id' configuration"
                                "\trepl Starts a repl using either lumo or planck"])))

(defn -main[& args]
  (let [{:keys [options arguments errors summ]}
        (parse-opts args cli-options :in-order true)
        platform (:platform options)]
    (case (first arguments)
      "deps" (show-deps (.cwd nproc))
      "repl" (run-repl platform (.cwd nproc) (next arguments))
      "build" (run-build  (.cwd nproc) (or (second arguments) "dev"))
      nil (println help))))



(nodejs/enable-util-print!)
(set! *main-cli-fn* -main)
