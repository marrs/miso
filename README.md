# Make It So! - A General Build Tool for Babashka

> [!CAUTION]
> *Make It So!* is experimental software. Use at your own risk!

I am writing *Make It So!* out of frustration with the myriad of build
tools that I have tried and failed to use productively.

The general syntax is currently something like this.

```clojure
(rule
  {"target/file" ["source/file" ...] ...}
  (fn [newer-files all-files]
    (comment "Perform your operations here")))
```

An example `Makefile.clj` might be
```clojure
(require '[miso.core :as miso :refer [rule target<-source]])
(require '[clojure.string :refer [replace join] :rename {replace sreplace
                                                         join sjoin}])
(require '[babashka.fs :as fs])
(require '[babashka.process :refer [pb pipeline process shell]])
(require '[clojure.java.io :as io])

(rule
  (target<-source
    #(sreplace % #"^src/pages" "build")
    (fs/glob "src/pages/" "**.html"))
  (fn [modified all]
    (doseq [[dst srcs] modified]
      (doseq [src srcs]
        (let [result (-> (process "fnlate" src)
                         (process {:out :write :out-file (io/file dst)}
                                  "tidy" "-qi" "--tidy-mark" "no")
                         deref)])))))
```

Put your rules in a file called `Makefile.clj` and run the file using Babashaka.
An example `bb.edn` is as follows:
```clojure
{:paths ["bb"]
 :deps {miso/miso {:local/root "path/to/miso"}}
 :tasks
 {miso (shell "bb Makefile.clj")}}
```

Then build your project in the shell with
```bash
bb miso
```
