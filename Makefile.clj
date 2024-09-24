(require '[miso.core :refer [make]])
(require '[miso.pass :refer [password]])
(require '[miso.rsync :refer [rsync remote-address]])
(require '[babashka.fs :as fs])
(require '[babashka.process :refer [shell]])
(require '[clojure.pprint :refer [pprint]]) ; TODO: Work out why I can't use `prn`

(defn greet
  "Say 'hello' to the user."
  []
  "Hello")

(defn- newer?
  "Returns true if f1 is newer than f2."
  [f1 f2]
  (> (fs/file-time->millis (fs/last-modified-time f1))
     (fs/file-time->millis (fs/last-modified-time f2))))

(defn- last-modified-file
  "Returns the file from files that was most recently touched.

  Only one file is returned, even if multiple files share the
  same timestamp. This is fine if you just need a file that is
  representative of when the change occured."
  [files]
  (->> files
    (reduce (fn [acc x]
              (if (newer? x acc)
                x acc)))))

(defn- compile-miso-core
  [sources]
  (let [target-dir "classes/"
        proc #(shell "clj -M -e"
                      "(compile 'miso.core)")
        ]
    (if (empty? (fs/list-dir target-dir))
      (proc)
      (let [newest-target (last-modified-file (fs/glob target-dir "**"))]
        (when (seq (fs/modified-since newest-target sources))
          (proc))))
    (fs/glob target-dir "**")))

(-> (fs/glob "src/" "**")

    compile-miso-core

    (make
      "target/miso.jar"
      (fn [_ _]
        (shell "clj -Sdeps '{:aliases {:uberjar {:replace-deps {uberdeps/uberdeps {:mvn/version \"1.4.0\"}} :replace-paths []}}}}'"
               "-M:uberjar"
               "-m"
               "uberdeps.uberjar"
               "--main-class"
               "miso.core")))

    (make
      "target/miso"
      (fn [modified _]
        (shell
          (clojure.string/join " "
                               ["native-image"
                                "-jar"
                                "target/miso.jar"
                                "--no-fallback"
                                "--initialize-at-build-time=\"\""
                                "target/miso"]
                               ))))
    )
