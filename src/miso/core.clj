; Run with bb --classpath ~/path/to/miso/src Makefile.clj
(ns miso.core
  (:gen-class)
  (:require [clojure.test :refer [function?]]
            [clojure.java.io :as io]
            [clojure.pprint]
            [babashka.fs :as fs]
            [sci.core :as sci]
            [sci.ctx-store :as ctx]
            [sci.impl.interpreter :as interp]

            [miso.core]
            [miso.pass]
            [miso.rsync]
            ))

; FIXME: This won't work after compilation with graal
; (-> f class .getDeclaredMethods) will return an empty list.
(defn- n-args [f]
  (-> f class .getDeclaredMethods first .getParameterTypes alength))

(defn mkdirp! [f]
  (-> f
      fs/path
      fs/create-dirs))

(defn filter-changeset
  "Returns a map of target file to sources files for
  those sources that are newer than their target."
  [target-map]
  (reduce (fn [acc [target src]]
            (let [target-file (io/file target)
                  sources? (coll? src)]
              (if-not (fs/exists? target-file)
                (assoc acc target-file src)
                (if-let [newer-inputs ((if sources? seq first)
                                       (fs/modified-since
                                         target-file
                                         (let [src (if sources?
                                                     src (list src))]
                                           (->> src
                                                (map str)
                                                (map io/file)))))]
                  (assoc acc target-file newer-inputs)
                  acc))))
          {}
          target-map))

(defn target<-source
  "Returns a map of target file to source files.
  If f is a function, it is used to generate a target filename
  from the source file.  Otherwise, it is considered a string
  and returned directly."
  [f sources]
  (reduce (fn [acc src]
            (let [target (if (function? f) (f src) f)]
              (assoc acc
                     target
                     (conj (get acc target []) src))))
          {} sources))

(defn for-changeset [target-map f]
  "Iterates over newer sources. Returns sequence of target files."
  (let [changeset (filter-changeset target-map)]
    (run! f changeset target-map)
    (keys changeset)))

(defmacro handle-changeset
  "Update modified time of any sources that failed to build so that they
   will be included in future builds."
  [changeset expr]
  `(try ~expr
        (catch Exception e#
          (println "Negative, Captain! Command exited with error.")
          (doseq [[dst# sources#] ~changeset]
            (doseq [src# sources#]
              (fs/set-last-modified-time src# (.now java.time.Instant)))))))

(defn make
  "Generates a map of targets to sources using mapper
  and then processes it with proc.

  Returns the complete list of target files expected."
  [sources mapper proc]
  (let [target-map (target<-source mapper sources)
        changeset (filter-changeset target-map)]
    (when (seq changeset)
      (handle-changeset changeset
        (case (n-args proc)
          0 (proc)
          1 (proc changeset)
          :else (proc changeset target-map))))
    (keys target-map)))

(defn make-multi
  "coll is an array of [mapper proc] pairs. This is used to
  generate a set of maps of targets to sources.  They are then
  processed with f.
  
  A list of targets, grouped by mapper, is provided to f, for
  final reduction to a single list of targets to be returned."
  [sources coll f]
  (->> coll
       (mapv #(apply make (concat [sources] %)))
       f))

(defn- namespaces []
  (reduce #(assoc %1 %2 (ns-publics %2))
          {}  ['clojure.pprint 'miso.core 'miso.pass 'miso.rsync 'babashka.fs 'babashka.process]))

(defn print-help
  ([cmd-map]
   (print-help cmd-map nil))
  ([cmd-map cmd]
   (if (nil? cmd)
     (do (println "Available commands:")
         (doseq [[ky vl] cmd-map]
           (print-help cmd-map ky)))
     (println "  " (str cmd) "\t" (-> (get cmd-map (symbol cmd)) meta :doc)))))

; The code within this symbol is executed within the context of the Makefile.
; Any publicly defined function will be run if its name matches the
; first arg passed from the CLI. 
(def run-command
  '(if-let [action (first miso.mfstate/args)]
     (let [public-members (ns-publics *ns*)]
       (if-let [fun (get public-members (symbol action))]
         (apply fun (rest miso.mfstate/args))
         (if (= action "help")
           (if-let [cmd (second miso.mfstate/args)]
             (miso.core/print-help public-members cmd)
             (miso.core/print-help public-members))
           (throw (ex-info "Action not defined in Makefile." {:action action})))))
     (when-not (nil? @miso.mfstate/build)
       (@miso.mfstate/build))))

(defmacro ->
  "Define what operations are to be run by default. It behaves like a standard threading
  macro except that running of its expression is deferred until after command line args
  have been evaluated."
  [& exprs]
  `(reset! miso.mfstate/build (fn [] ~(cons '-> exprs))))

; The following vars are defined for the context the Makefile is evaluated under:
; - miso.mfstate/args (the command line args that were passed to miso)
; - miso.mfstate/build (the function to be run by default - created by miso.core/->)
(defn load-makefile
  [& args]
  (let [f (io/file "Makefile.clj")
        s (slurp f)
        ctx-args (sci/new-var 'args args {:dynamic true :private true})
        ctx (sci/init {:namespaces (assoc (namespaces)
                                          'miso.mfstate
                                          {'args ctx-args
                                           'build (atom (fn []))})})]
    (try
      ; {:classes {:allow :all}} was added in the hope that it would allow reflection
      ; of the lambdas passed to make et al, but it didn't work.  It doesn't look like
      ; Babashka or GraalVM support it. Keeping in until confirmed.
      ; If removed, we should go back to using `sci.core/eval-string*`.
      (sci/binding [sci/out *out*, sci/err *err*]
        (interp/eval-string* ctx
                             (str s "\n" run-command)
                             {:classes {:allow :all} :sci.impl/eval-string+ true}))

      (catch clojure.lang.ExceptionInfo e
        (clojure.pprint/pprint (clojure.core/-> e ex-data))))))

(defn -main [& args]
  (apply load-makefile args))
