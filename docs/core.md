# cljs-node-io.core

  * [x]__delete-file__
    - ```(delete-file f & [silently])```
    - Delete file f. Raise an exception if it fails unless silently is true.


  * [x]__file__
    - ```(file arg)```
    - ```(file parent child)```
    - ```(file parent child & more)```
    - Returns a reified File, passing each arg to as-file.  Multiple-arg versions treat the first argument as parent and subsequent args as children relative to the parent.      


  * [x]__make-parents__
    - ```(make-parents f & more)```
    - Given the same arg(s) as for file, creates all parent directories of the file they represent.


  * [x]__as-relative-path__
    - ```(as-relative-path x) ```
    - Take an as-file-able thing and return a string if it is a relative path, else IllegalArgumentException.







  * __spit__
    - [x] sync
    - [x] async   




  * __slurp__
    - [x] sync
     -  NOT bufferedFileReader+FileStream as in clojure. Nodejs's streams are created
      asynchronously and would require slurp to return a channel. This uses
      FS.readFileSync, fine for small files. Use FileInputStream for more flexibility
    - [x] async




  * [x]__sslurp__
    - *super* slurp, convenience over slurp
    - automatically reads edn+json file into clj data-structures