(ns airhead.libshout
  (:require [clj-native.direct :as jna])
  (:import (java.io InputStream)
           (java.nio IntBuffer ByteBuffer)))

(jna/defclib libshout
  (:libname "libshout")
  (:functions
   (shout_version [int* int* int*] constchar*)
   (shout_init [])
   (shout_shutdown [])
   (shout_new [] void*)
   (shout_set_host [void* constchar*] int)
   (shout_set_port [void* short] int)
   (shout_set_user [void* constchar*] int)
   (shout_set_password [void* constchar*] int)
   (shout_set_mount [void* constchar*] int)
   (shout_open [void*] int)
   (shout_close [void*] int)
   (shout_send [void* byte* size_t] int)
   (shout_sync [void*])))

(defn- check-err [f & args]
  (let [r (apply f args)]
    (when-not (= 0 r)
      (throw (Exception. (str "Libshout error: " r))))))

(defn init-lib!
  "Call this once before ever using the lib."
  []
  (jna/loadlib libshout)
  (shout_init))

(defn deinit-lib!
  "Call this once when you're done using the lib."
  []
  (shout_shutdown))

(defn version []
  (let [a (IntBuffer/allocate 1)
        b (IntBuffer/allocate 1)
        c (IntBuffer/allocate 1)
        s (shout_version a b c)]
    (seq [(.get a 0) (.get b 0) (.get c 0)])))

(defn open
  [{:keys [addr port username password mount]
    :or   {addr     "127.0.0.1"
           port     8000
           username "source"
           password "hackme"
           mount    "airhead"}}]
  (if-let [handle (shout_new)]
    (do
      (check-err shout_set_host handle addr)
      (check-err shout_set_port handle port)
      (check-err shout_set_user handle username)
      (check-err shout_set_password handle password)
      (check-err shout_set_mount handle mount)
      handle)
    (Exception. "Could not allocate a new shout_t data structure.")))

(defn close! [handle]
  (check-err shout_close handle))

(defn send-bytes!
  ([handle bytes]
   (check-err shout_send handle (ByteBuffer/wrap bytes) (count bytes))
   (shout_sync handle))
   ([handle bytes len]
    (check-err shout_send handle (ByteBuffer/wrap bytes) len)
    (shout_sync handle)))

(defn send-input-stream! [handle ^InputStream in-stream continue?]
  (let [buffer (byte-array 4096)
        read!  #(.read in-stream buffer)]
    (loop [len (read!)]
      (when (and (pos? len) @continue?)
        (send-bytes! handle buffer len)
        (recur (read!))))))
