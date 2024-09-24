; Run with bb --classpath ~/path/to/miso/src Makefile.clj
(ns miso.core
  (:gen-class)
  (:require [clojure.test :refer [function?]]
            [clojure.java.io :as io]
            [babashka.fs :as fs]
            [sci.core :as sci]
            [sci.ctx-store :as ctx]

            [miso.core]
            [miso.pass]
            [miso.rsync]
            ))

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

(defn make-target
  "Generates an injective map of targets to sources using mapper
  and then processes it with proc!.

  Returns the complete list of target files expected."
  [sources mapper proc!]
  (let [target-map (target<-source mapper sources)
        changeset (filter-changeset target-map)]
    (when (seq changeset)
      (proc! changeset target-map))
    (keys target-map)))

(defn make-targets
  "coll is an array of [mapper proc!] pairs. This is used to
  generate a set of injective maps of targets to sources.  They
  are then processed with proc!
  
  A list of targets, grouped by mapper, is provided to f, for
  final reduction to a single list of targets to be returned."
  [sources coll f]
  (->> coll
       (mapv #(apply make-target (concat [sources] %)))
       f))

(defn load-makefile
  []
  (let [f (io/file "Makefile.clj")
        s (slurp f)
        ctx (sci/init
              {:namespaces {'miso.core (ns-publics 'miso.core)
                            'miso.pass (ns-publics 'miso.pass)
                            'miso.rsync (ns-publics 'miso.rsync)}})]
    (sci/eval-string* ctx s)))

(defn -main []
  (prn (load-makefile)))
