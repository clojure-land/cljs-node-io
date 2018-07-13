(ns cljs-node-io.proc
  "A thin wrapper over child_process"
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [cljs-node-io.macros :refer [goog-typedef]])
  (:require [cljs.core.async :as casync :refer [put! take! chan pipe close! promise-chan]]
            [cljs.core.async.impl.protocols :as impl]
            [cljs-node-io.protocols :refer [IChildProcess]]
            [clojure.string :as string :refer [split-lines]]))

(def childproc (js/require "child_process"))

(defn checked-env
  "when opts contains :env, merges it with process.env"
  [opts]
  (assert (map? opts))
  (if-let [env (get opts :env)]
    (let [ENV (js/Object.create js/process.env)]
      (doseq [[k v] env]
        (goog.object.set ENV k v))
      (assoc opts :env ENV))
    opts))

(defn exec
  "@return {(buffer.Buffer|String)} the stdout from the command"
  ([cmdstr](exec cmdstr nil))
  ([cmdstr opts]
   (let [opts (checked-env (or opts {}))]
     (childproc.execSync cmdstr (clj->js opts)))))

(goog-typedef PortedChildProcess
  "@typedef {!child_process.ChildProcess}
   @implements {impl/ReadPort}")

(defn aexec
  "@param {!string} cmdstr :: command with space separated args
   @param {!IMap} options :: see https://nodejs.org/api/child_process.html#child_process_child_process_exec_command_options_callback
   @return {!PortedChildProcess} :: childprocess implementing readport.
     - This allows sync access to CP properties and methods
     - channel yields [Error {string|Buffer} {string|Buffer}]"
  ([cmdstr](aexec cmdstr {}))
  ([cmdstr opts]
   (let [out (promise-chan)
         opts (checked-env (or opts {}))
         cb (fn [err stdout stderr]
              (put! out [err stdout stderr]))]
     (specify! (childproc.exec cmdstr (clj->js opts) cb)
        impl/ReadPort
        (take! [_ handler] (impl/take! out handler))))))

(defn execFile
  "@param {!string} pathstr :: the file to execute
   @param {!IVector} args :: args to the executable
   @param {!IMap} opts :: execution options
   @return {(buffer.Buffer|String)}"
  [pathstr args opts]
  (let [opts (checked-env (or opts {}))]
    (childproc.execFileSync pathstr (into-array args) (clj->js opts))))

(defn aexecFile
  "@param {!string} pathstr :: the file to execute
   @param {!IVector} args :: args to the executable
   @param {!IMap} opts :: execution options
   @return {!PortedChildProcess} :: childprocess implementing readport.
     - This allows sync access to CP properties and methods
     - channel yields [Error {string|Buffer} {string|Buffer}]"
  [pathstr args opts]
  (let [out (promise-chan)
        opts (checked-env (or opts {}))
        cb (fn [err stdout stderr] (put! out [err stdout stderr]))]
    (specify! (childproc.execFile pathstr (into-array args) (clj->js opts) cb)
      impl/ReadPort
      (take! [_ handler] (impl/take! out handler)))))

(defn spawn-sync
  "An exception to the 'a' prefix rule: cp.spawnSync will block until its
   process exits before returning a modified ChildProcess object. This is
   significantly less useful than a persisting asynchronous spawn
   @param {!string} cmd :: command to execute in a shell
   @param {!IVector} args :: args to the shell command
   @param {!IMap} opts :: map of execution options
   @return {!Object}"
  [cmd args opts]
  (let [opts (checked-env (or opts {}))]
    (childproc.spawnSync cmd (into-array args) (clj->js opts))))

;; https://nodejs.org/api/child_process.html#child_process_child_process_spawn_command_args_options
(defn spawn
  "@param {!string} cmd :: command to execute in a shell
   @param {!IVector} args :: args to the shell command
   @param {!IMap} opts :: execution options
   @return {!child_process.ChildProcess}"
  [cmd args opts]
  (let [opts (checked-env (or opts {}))]
    (childproc.spawn cmd (into-array args) (clj->js opts))))

(defn fork
  "@param {!string} modulePath :: path to js file to run
   @param {!IVector} args :: arguments to the js file
   @param {!IMap} opts :: map of execution options
   @return {!child_process.ChildProcess}"
  [modulePath args opts]
  (let [args (apply array args)
        opts (checked-env (merge {:silent true :stdio "pipe"} opts))]
    (childproc.fork modulePath args (clj->js opts))))

