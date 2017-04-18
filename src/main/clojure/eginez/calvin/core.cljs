(ns eginez.calvin.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [clojure.string :as str])
  (:require [clojure.string :as str]
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

(def debug? (atom false))

(defn println-err [& args]
  (binding [*print-fn* *print-err-fn*]
    (apply println args)))

(defn warn [& args]
  (apply println-err "WARNING:" args))

(defn fatal [& args]
  (apply println-err "FATAL:" args)
  (js/process.exit 1))

(defn debug [& args]
  (when @debug?
    (apply println-err args)))

(defn find-file [fpath]
  (try
    (let [files (.readdirSync fs fpath)
          fname (-> (filter #(str/includes? % "project.clj") files)
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

(defn find-build-from-vector [builds id]
  (let [build (-> (filter #(= (:id %) id) builds) first)]
    (if build
      build
      (do
        (warn "No build with id" (str "`" id "'") "found, falling back to" (str "`" (:id (first builds)) "'"))
        (first builds)))))

(defn find-build-from-map [builds id]
  (let [build (get builds id (get builds (keyword id)))]
    (if build
      build
      (do
        (warn "No build with id" (str "`" id "'") "found, falling back to" (str "`" (key (first builds)) "'"))
        (val (first builds))))))

(defn find-cljsbuild-build [project id]
  (let [builds (get-in project [:cljsbuild :builds])]
    (cond
      (not (seq builds)) (do (fatal "No cljsbuild :builds configured.") nil)
      (vector? builds) (find-build-from-vector builds id)
      (map? builds) (find-build-from-map builds id)
      :else (fatal "cljsbuild :builds configuration must be a vector or a map, got" (prn builds)))))

(defn find-source-path [build]
  (let [source-paths (:source-paths build)
        dropped (rest source-paths)]
    (when-not (vector? source-paths)
      (fatal ":source-paths must be a vector, got" (prn source-paths)))
    (when (seq dropped)
      (warn "Current lumo api does not support multiple sources, dropping " dropped))
    (first source-paths)))

(defn find-compiler-opts [build]
  (let [opts (:compiler build)
        main (:main opts)
        target (:target opts)]
    (when-not (= target :nodejs)
      (warn "The compile target should be :nodejs, got" (prn target) ". Try adding {:compiler {:target :nodejs}}." ))
    (assoc opts :main `'~main)))

(defn build-build-command [src-projects compiler-options]
  (let [b `(b/build ~src-projects ~compiler-options)]
    (->> (map str [build-preface b])
         (str/join " "))))

(defn resolve-classpath [project]
  (go
    (let [deps (:dependencies project)]
      (when deps
        (let [dep-list (<! (resolve-dependencies deps true))]
          (str/join ":" (map hb/dep->path dep-list)))))))

(defn print-dep-tree [root graph depth]
  (let [art (first (filter #(samedep? root %) (keys graph)))
        deps (get graph art)]
    (println (str/join (concat (repeat depth "*") ">")) (hb/dep->coordinate art))
    (if (not-empty deps)
      (doseq [n deps]
        (print-dep-tree n graph (inc depth))))))

(defn lumo-build-cmd [project id classpath]
  (let [build (find-cljsbuild-build project id)
        src-path (find-source-path build)
        compiler-options (find-compiler-opts build)
        build-cmd (build-build-command src-path compiler-options)
        final-cmd (str "\"" (str/replace-all build-cmd #"\"" "\\\"") "\"")]
    (debug "build lumo cmd with " final-cmd " and path " classpath)
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

(defn show-deps [project]
  (go
    (println "Calculating dependencies")
    (if-let [coordinates (:dependencies project)]
      (let [[status dep-graph dep-list] (<!(resolve-dependencies coordinates false))
            root (dissoc (first dep-graph) :exclusions)
            dg (second dep-graph)
            [head-dep & _] (filter #(samedep? root %) (keys dg))]
        (show-all-deps dep-graph))
      (warn "No dependencies file found are you missing a project.clj or boot.clj?"))))
      ;;(print-dep-tree head-dep dg 0)

(defn run-build [project id]
  (go
    (let [classpath (<! (resolve-classpath project))
          [bin args] (lumo-build-cmd project id classpath)
          proc (do
                 (.spawn nchild bin (clj->js args) (clj->js {:stdio [0 1 2] :shell true}))
                 (apply debug "Starting build:" bin args))]
      proc)))

(defn run-repl [platform project rest-args build-id]
  (go
    (let [build (find-cljsbuild-build project build-id)
          src-path (find-source-path build)
          classpath (<! (resolve-classpath project))
          classpath (->> [src-path classpath]
                         (remove #(or (nil? %) (empty? %)))
                         (str/join ":"))
          [bin args] (build-cmd-for-platform platform classpath)
          args (concat args rest-args)
          proc (do
                 (apply debug "Starting REPL:" bin args)
                 (.spawn nchild bin (clj->js args) (clj->js {:stdio [0 1 2]})))]
      proc)))

(def cli-options [["-h" "--help"]
                  ["-d" "--debug" "Show debug information" :default false]
                  ["-i" "--build-id" "Set the cljsbuild build id. Defaults to 'dev'" :default "dev"]
                  ["-p" "--platform PLATFORM" "Either planck or lumo" :default "lumo"]])

(def help
  (str/join \newline (flatten ["Calvin a minimalistic build tool for clojurescript"
                               "Usage: calvin [options] args"
                               "Options:"
                               (map #(str "\t" (str/join " " (take 2 %))) cli-options)
                               "Arguments:"
                               "\tdeps Shows dependencies"
                               "\tbuild [id] Builds the project using the 'id' configuration"
                               "\trepl Starts a repl using either lumo or planck"])))

(defn -main[& args]
  (let [{:keys [options arguments errors summ]}
        (parse-opts args cli-options :in-order true)
        platform (:platform options)
        cwd (.cwd nproc)
        project (find-lein-project-configuration (find-file (.cwd nproc)))]
    (reset! debug? (:debug options))
    (case (first arguments)
      "deps" (show-deps project)
      "repl" (run-repl platform project (next arguments) (:build-id options))
      "build" (run-build project (or (second arguments) (:build-id options)))
      nil (println-err help))))



(nodejs/enable-util-print!)
(set! *main-cli-fn* -main)
