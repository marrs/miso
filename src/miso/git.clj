(ns miso.git
   (:require [clojure.test :refer [function?]]
             [clojure.java.io :as io]
             [babashka.process :refer [shell]]))

(defn first-commit []
  "Returns first commit hash as a string."
  (->> "git rev-list --reverse HEAD"
          (shell {:out :string})
          :out
          (re-find #"^(.*)\n" )
          second))

(defn changeset
  "Returns the files that have changed between :to and :from of _range
  (if provided), sorted by status, and filtered by the paths (if provided)."
  [_range & paths]
  (let [{:keys [from to] :or {from (first-commit) to :HEAD}} (if (map? _range) _range {})
        paths (if (string? _range) (conj paths _range) paths)
        files (-> (apply shell
                         (concat [{:out :string}
                         "git" "diff-tree" "--no-commit-id" "--name-status" "-r"
                         (name from) (name to)] paths))
                  :out
                  (clojure.string/split #"\n"))]
    (->> files
          (map (fn [x]
                 (let [matches (re-find #"^([A-Z])\s(.*)$" x)]
                   [(case (second matches)
                      "A" :added
                      "M" :modified
                      "D" :deleted
                      "T" :type-changed ;untested
                      "R" :renamed ; untested
                      "C" :copied) ; untested
                    (last matches)])))
          (reduce (fn [acc [status filename]]
                    (assoc acc status (conj (get acc status []) filename)))
                  {}))))
