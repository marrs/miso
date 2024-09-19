; Run with bb --classpath ~/path/to/miso/src Makefile.clj
(ns miso.core
   (:require [clojure.test :refer [function?]]
             [clojure.java.io :as io]
             [babashka.fs :as fs]))

(defn mkdirp! [f]
  (-> f
      fs/path
      fs/create-dirs))

(defn filter-changeset
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
  "Returns an injective map of source file to target file.
  If f is a function, it is used to generate a target filename
  from the source file.  Otherwise, it is considered a string
  and returned directly."
  [f sources]
  (reduce #(assoc %1
                  (if (function? f) (f %2) f)
                  %2)
          {} sources))

(defn targets<-source
  "Returns a map of target files to source file.
  Target filenames are generated using fc, which generates
  a collection of target files from a source file"
  [fc sources]
  (map #(apply target<-source [% sources]) fc))

(defn for-changeset [target-map f]
  "Iterates over newer sources. Returns sequence of target files."
  (let [newer (filter-changeset target-map)]
    (run! f newer target-map)
    (keys newer)))

(defn make-target
  "Generates an injective map of targets to sources using mapper
  and then processes it with proc!.

  Returns the complete list of target files generated"
  [sources mapper proc!]
  (let [target-map (target<-source mapper sources)
        changeset (filter-changeset target-map)]
    (proc! changeset target-map)
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
