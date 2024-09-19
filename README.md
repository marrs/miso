# Make It So! - A General Build Tool for Babashka

> [!CAUTION]
> *Make It So!* is experimental software. Use at your own risk!

I am writing *Make It So!* out of frustration with the myriad of build
tools that I have tried and failed to use productively.

The general syntax is currently something like this.

```clojure
(make-target
  ["source/file" ...]
  target-filename-mapper
  (fn [modified-files all-files]
    (comment "Perform your operations here")))
```

`make-target` behaves in the following way:
- Provide it with:
  1. a list of source files;
  2. a single target file or a function that generates a target filename
     for each source file;
  3. a function with the operation to run to generate the target
     file(s) from the source files.
     - 1st argument is only the source files that are newer than their
       corresponding target file.
     - 2nd argument is all of the source files.
- It returns a list containing the target file or files.

A simple `Makefile.clj` for compiling a C programme called `edit` may
look something like the following (example taken from [gnu.org](https://www.gnu.org/software/make/manual/html_node/Simple-Makefile.html)):
```clojure
(-> (concat (make-target ["main.c" "defs.h"] "main.o"
              #(shell "cc -c main.c"))
            (make-target ["kbd.c" "defs.h" "command.h"] "kbd.o"
              #(shell "cc -c kbd.c"))
            (make-target ["command.c" "defs.h" "command.h"] "command.o"
              #(shell "cc -c command.c"))
            (make-target ["display.c" "defs.h" "buffer.h"] "display.o"
              #(shell "cc -c display.c"))
            (make-target ["insert.c" "defs.h" "buffer.h"] "insert.o"
              #(shell "cc -c insert.c"))
            (make-target ["search.c" "defs.h" "buffer.h"] "search.o"
              #(shell "cc -c search.c"))
            (make-target ["files.c" "defs.h" "buffer.h" "command.h"] "files.o"
              #(shell "cc -c files.c"))
            (make-target ["utils.c" "defs.h"] "utils.o"
              #(shell "cc -c utils.c")))
    (make-target "edit"
      #(apply shell (concat ["cc -o edit"] %2)))
```

This example `Makefile.clj` generates a static website from a list of html
files:
```clojure
(require '[miso.core :as miso :refer [make-target target<-source]])
(require '[clojure.string :refer [replace] :rename {replace sreplace}])
(require '[babashka.fs :as fs])
(require '[babashka.process :refer [process shell]])
(require '[clojure.java.io :as io])


(make-target
  (fs/glob "src/pages/" "**.html")    ; Generate list of source files and a
  #(sreplace % #"^src/pages" "build") ; mapping to their assocated target files.
  (fn [modified _]
    ; `modified` is a list of those source files that are newer than their targets.
    (doseq [[target sources] modified]
      (doseq [src sources]
        (let [result (-> (process "generate-static-page" src)
                         (process {:out :write :out-file (io/file target)}
                                  "tidy" "-qi" "--tidy-mark" "no")
                         deref)]))))
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
