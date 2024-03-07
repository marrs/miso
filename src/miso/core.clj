; Run with bb --classpath ~/path/to/miso/src Makefile.clj
(ns miso.core
   (:require [clojure.test :refer [function?]]
             [clojure.java.io :as io]
             [babashka.fs :as fs]))

(defn mkdirp! [f]
  (-> f
      fs/parent
      fs/path
      fs/create-dirs))

(defn filter-newer-inputs
  "Returns a map of targets from sources for those
  sources that are newer than their target."
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
  "Returns a one-to-one map of target file to source file.
  Target filename is generated from source file using f."
  [f sources]
  (reduce #(assoc %1 (f %2) %2) {} sources))

(defn targets<-source
  "Returns a map of target files to source file.
  Target filenames are generated using f, which generates
  a collection of target files from a source file"
  [f source]
  (reduce (fn [acc s] (assoc acc (map #(% s) f) s)) {} sources))


(defn rule
  "Given a map of targets to sources and a function (f [m a]),
  return (f m a) where m is a modified map containing only
  targets with newer sources, and a is the original map.
  
  If none of the sources belonging to a target are newer than the target,
  the entire entry will be omitted from the map."
  [target-map f]
  (let [changeset (filter-newer-inputs target-map)]
    ; TODO: If any of the inputs have deps that are newer than target,
    ; filter those inputs in as well.
    (f changeset target-map)))
