(ns inspector.core
  (:require [inspector.impl :as impl :refer :all]
            [clojure.reflect]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

(defonce ^:dynamic *writer* nil)

(defn set-writer!
  "Changes the writer object with which inspector prints messages. If set to
  nil, *out* is used. If writer is not an instance of java.io.Writer,
  clojure.java.io/writer is used to coerce it into one."
  [writer]
  (alter-var-root
   #'*writer*
   (constantly (if (instance? java.io.Writer writer)
                 writer
                 (when writer (io/writer writer))))))

(defmacro whereami
  "Prints the current stack trace"
  []
  `(println (str/join "\n" (impl/stack-trace))))

(defmacro env
  "Returns the current lexical bindings as a sorted map"
  []
  (into {}
        (for [k (keys &env)
              :when (not (str/ends-with? (str k) "__auto__"))]
          [(keyword k) (symbol k)])))

(defmacro inspect
  "Inspects the form and returns the inspection result as map"
  [form]
  `(let [started# (System/currentTimeMillis)
         result#  ~form
         elapsed# (- (System/currentTimeMillis) started#)]
     {:form    '~form
      :started started#
      :result  result#
      :elapsed elapsed#
      :locals  (env)}))

(defmacro ?
  "Executes the forms and prints debug information to *out*"
  [& forms]
  `(binding [*out* (or *writer* *out*)]
     ~@(for [[idx form] (map vector (iterate inc 0) forms)]
         `(binding [impl/*indent* (inc impl/*indent*)]
            (let [env#   (env)
                  len#   (apply max 0 (map (comp count name) (keys env#)))
                  form#  '~(trim-form form)
                  eval?# (or (list? '~form) (symbol? '~form))]
              (when (and (zero? impl/*indent*) (zero? ~idx))
                (output ""))
              (output "┌ " ((if eval?# ansi-str ansi-str-multiline) form#))
              (output "│ " (wrap-ansi (first (stack-trace)) "30;1"))
              (let [started# (System/currentTimeMillis)
                    result#  ~form
                    elapsed# (- (System/currentTimeMillis) started#)]
                ;; No need to print the evaluation result if not symbol nor list
                (when eval?#
                  (output "│  result:  " (ansi-str-multiline result#))
                  (when (pos? elapsed#)
                    (output "│  elapsed: " (str elapsed# "ms"))))
                ;; Print locals
                (when (seq env#)
                  (output "├  locals:")
                  (doseq [k# (sort (keys env#))
                          :when (not= *out* (env# k#))]
                    (output (format (str "│    %-" (inc len#) "s ")
                                    (str (name k#) ":"))
                            (ansi-str (env# k#)))))
                (output "└ ")
                result#))))))

(defn- inspect-form
  "Reader function for ?"
  [form]
  `(? ~form))

(defn ls
  "Prints the members of a namespace, or public members of an Object"
  ([]
   (ls *ns*))
  ([obj]
   (if (instance? clojure.lang.Namespace obj)
     (pp/print-table [:sym :var :doc]
                     (->> (for [[k v] (ns-publics obj)]
                            {:sym k
                             :var v
                             :doc (some-> v meta :doc str/split-lines first)})
                          (sort-by :sym)))
     (pp/print-table [:category :flags :name :parameters :type]
                     (->> obj
                          clojure.reflect/reflect
                          :members
                          (map impl/inspect-member)
                          (filter (every-pred :public? (complement :bridge?)))
                          (sort-by (juxt :category :static? :name :parameters)))))))
