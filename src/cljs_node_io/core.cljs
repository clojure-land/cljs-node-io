(ns cljs-node-io.core
  (:require [cljs.nodejs :as nodejs :refer [require]]
            [cljs-node-io.file :refer [File]]
            ; [cljs-node-io.reader :refer [reader]]
            [cljs.reader :refer [read-string]]
            [cljs-node-io.streams :refer [FileInputStream]]
            [cljs-node-io.protocols
              :refer [Coercions as-url as-file
                      IOFactory make-reader make-writer make-input-stream make-output-stream]]
            [clojure.string :as st]
            [goog.string :as gstr])
  (:import goog.Uri
           [goog.string StringBuffer]))

(nodejs/enable-util-print!)

(def fs (require "fs"))
(def path (require "path"))
(def Buffer (.-Buffer (require "buffer")))

(extend-protocol Coercions
  nil
  (as-file [_] nil)
  (as-url [_] nil)
  string
  (as-file [s] (File. s))
  (as-url [s] (.getPath (Uri. s)))
  Uri
  (as-url [u] (.getPath u))
  (as-file [u]
    (if (= "file" (.getScheme u))
      (as-file (.getPath u)) ;goog.Uri handles decoding woohoo
      (throw (js/Error. (str "IllegalArgumentException : Not a file: " u))))))

(defn ^String as-relative-path
  "Take an as-file-able thing and return a string if it is
   a relative path, else IllegalArgumentException."
  [x]
  (let [^File f (as-file x)]
    (if (.isAbsolute f)
      (throw (js/Error. (str "IllegalArgumentException: " f " is not a relative path")))
      (.getPath f))))


(defn ^File file
  "Returns a reified file, passing each arg to as-file.  Multiple-arg
   versions treat the first argument as parent and subsequent args as
   children relative to the parent."
  ([arg]
   (as-file arg))
  ([parent child]
   (File. ^File (as-file parent) ^String (as-relative-path child)))
  ([parent child & more]
   (reduce file (file parent child) more)))

(defn delete-file
  "Delete file f. Raise an exception if it fails unless silently is true."
  [f & [silently]]
  (or (.delete (file f))
      silently
      (throw (js/Error. (str "Couldn't delete " f)))))

(defn ^Reader reader
  "Attempts to coerce its argument into an open java.io.Reader.
   Default implementations always return a java.io.BufferedReader.
   Default implementations are provided for Reader, BufferedReader,
   InputStream, File, URI, URL, Socket, byte arrays, character arrays,
   and String.
   If argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.
   Should be used inside with-open to ensure the Reader is properly
   closed."
  [x & opts]
  (make-reader x (when opts (apply hash-map opts))))

(defn ^Writer writer
  "Attempts to coerce its argument into an open java.io.Writer.
   Default implementations always return a java.io.BufferedWriter.
   Default implementations are provided for Writer, BufferedWriter,
   OutputStream, File, URI, URL, Socket, and String.
   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.
   Should be used inside with-open to ensure the Writer is properly
   closed."
  [x & opts]
  (make-writer x (when opts (apply hash-map opts))))

(defn ^InputStream input-stream
  "Attempts to coerce its argument into an open java.io.InputStream.
   Default implementations always return a java.io.BufferedInputStream.
   Default implementations are defined for InputStream, File, URI, URL,
   Socket, byte array, and String arguments.
   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.
   Should be used inside with-open to ensure the InputStream is properly
   closed."
  [x & opts]
  (make-input-stream x (when opts (apply hash-map opts))))

(defn ^OutputStream output-stream
  "Attempts to coerce its argument into an open java.io.OutputStream.
   Default implementations always return a java.io.BufferedOutputStream.
   Default implementations are defined for OutputStream, File, URI, URL,
   Socket, and String arguments.
   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.
   Should be used inside with-open to ensure the OutputStream is
   properly closed."
  {:added "1.2"}
  [x & opts]
  (make-output-stream x (when opts (apply hash-map opts))))


(defn- ^Boolean append? [opts]
  (boolean (:append opts)))

(defn- ^String encoding [opts]
  (or (:encoding opts) "utf8"))

(defn- buffer-size [opts]
  (or (:buffer-size opts) 1024)) ;<==?



(extend-protocol IOFactory
  Uri
  (make-reader [x opts] (make-reader (make-input-stream x opts) opts))
  (make-writer [x opts] (make-writer (make-output-stream x opts) opts))
  (make-input-stream [x opts] (make-input-stream
                                (if (= "file" (.getScheme x)) ;move this to make-reader?
                                  (FileInputStream. (as-file x))
                                  (.openStream x)) opts))
  (make-output-stream [x opts] (if (= "file" (.getScheme x))
                                 (make-output-stream (as-file x) opts)
                                 (throw (js/Error. (str "IllegalArgumentException: Can not write to non-file URL <" x ">")))))

  string
  (make-reader [x opts] (make-reader (as-file x) opts)); choice to make stream is handled by opts passed to reader
  (make-writer [x opts] (make-writer (as-file x) opts))
  (make-input-stream [^String x opts](try
                                        (make-input-stream (Uri. x) opts)
                                        (catch js/Error e ;MalformedURLException
                                          (make-input-stream (File. x) opts))))
  (make-output-stream [^String x opts] (try
                                        (make-output-stream (Uri. x) opts)
                                          (catch js/Error err ;MalformedURLException
                                              (make-output-stream (File. x) opts)))))


(defn slurp
  "Opens a reader on f and reads all its contents, returning a string.
  See reader for a complete list of supported arguments.
  Punts reading to file handling of opts. If not :stream? true, simply
  reads the file synchronously and returns its string contents"
  ([f & opts] (apply reader f opts)))

(defn sslurp
  "augmented 'super' slurp for convenience. edn|json => clj data-structures"
  [filepath]
  (let [contents (slurp filepath)]
    (condp = (.extname path filepath)
      ".edn"  (read-string contents)
      ".json" (js->clj (js/JSON.parse contents) :keywordize-keys true)
      ;xml, csv
      (throw (js/Error. "sslurp was given an unrecognized file format.
                         The file's extension must be json or edn")))))


(defn spit
  "Opposite of slurp.  Opens f with writer, writes content.
   Options passed to a file/file-writer."
  [f content & options]
  (let [w (apply writer f options)]
    (.write w (str content))))

; (defn line-seq
;   "Returns the lines of text from rdr as a lazy sequence of strings.
;   rdr must implement java.io.BufferedReader."
;   {:static true}
;   [^java.io.BufferedReader rdr]
;   (when-let [line (.readLine rdr)]
;     (cons line (lazy-seq (line-seq rdr)))))


(defn file-seq
  "taken from clojurescript/examples/nodels.cljs"
  [dir]
  (tree-seq
    (fn [f] (.isDirectory (.statSync fs f) ()))
    (fn [d] (map #(.join path d %) (.readdirSync fs d)))
    dir))

(defn xml-seq
  "A tree seq on the xml elements as per xml/parse"
  {:static true}
  [root]
    (tree-seq
     (complement string?)
     (comp seq :content)
     root))

(defn make-parents
  "Given the same arg(s) as for file, creates all parent directories of
   the file they represent."
  [f & more]
  (when-let [parent (.getParentFile ^File (apply file f more))]
    (.mkdirs parent)))

(defn copy-filestream [input output opts] nil)

(defmulti
  ^{:doc "Internal helper for copy"
     :private true
     :arglists '([input output opts])}
  do-copy
  (fn [input output opts] [(type input) (type output)]))


(defmethod do-copy [:File js/String] [^File input ^File output opts]
  (if (:stream? opts)
    (copy-filestream input output opts)
    (let [in (slurp input)]
     (spit output in opts))))

(defmethod do-copy [js/String :File] [^File input ^File output opts]
  (if (:stream? opts)
    (copy-filestream input output opts)
    (let [in (slurp input)]
     (spit output in opts))))

(defmethod do-copy [js/String js/String] [^File input ^File output opts]
  (if (:stream? opts)
    (copy-filestream input output opts)
    (let [in (slurp input)]
     (spit output in opts))))


(defmethod do-copy [:File :File] [^File input ^File output opts]
  (if (:stream? opts)
    (copy-filestream input output opts)
    (let [in (slurp input)]
     (spit output in opts))))

(defn copy
  "Copies input to output.  Returns nil or throws IOException.
  Input may be an InputStream, Reader, File, byte[], or String.
  Output may be an OutputStream, Writer, or File.
  Options are key/value pairs and may be one of
    :buffer-size  buffer size to use, default is 1024.
    :encoding     encoding to use if converting between
                  byte and char streams.
  Does not close any streams except those it opens itself
  (on a File)."
  ; :stream? option to use async stream readers vs sync
  [input output & opts]
  (do-copy input output (when opts (apply hash-map opts))))

(defn -main [& args] nil)
(set! *main-cli-fn* -main)
