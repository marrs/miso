; Run with bb --classpath ~/path/to/miso/src Makefile.clj
(ns miso.core
   (:require [clojure.test :refer [function?]]
             [clojure.java.io :as io]
             [babashka.fs :as fs]))

(defn filter-newer-inputs [target-map]
  (reduce (fn [acc [target srcs]]
            (let [target-file (io/file target)]
              (if-not (fs/exists? target-file)
                (assoc acc target-file srcs)
                (if-let [newer-inputs (seq (fs/modified-since
                                             target-file
                                             (->> srcs
                                                  (map #(.toString %))
                                                  (map io/file))))]
                  (assoc acc target-file newer-inputs)
                  acc))))
          {}
          target-map))

(defn target<-source
  "Return a one-to-one map of target file to source file.
  Target filename is generated from source file using f."
  [f sources]
  (reduce #(assoc %1 (f %2) [%2]) {} sources))

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
