(ns inspector.impl
  "This namespace contains functions that are not supposed to be directly used
  by the users"
  (:require [clojure.stacktrace :as trace]
            [clojure.string :as str]
            [clojure.walk :as w]
            [puget.printer :as puget]))

(defonce ^:dynamic *indent* -1)

(defn output
  ([label]
   (output label ""))
  ([label s]
   (println
    (let [indent (str/join (repeat (- (count label) 2) " "))
          lines  (str/split-lines s)
          lines  (map #(let [[line-no line] %
                             first-line? (zero? line-no)]
                         (str (str/join (repeat (if first-line?
                                                  *indent*
                                                  (inc *indent*)) "│ "))
                              (if first-line? label indent)
                              line))
                      (map vector (iterate inc 0) lines))]
      (str/join "\n" lines)))))

(defn trim-ansi-str
  [s max-width]
  (let [m ^java.util.regex.Matcher (re-matcher #"\u001b\[[0-9;]+m" s)
        ellipsis ".."
        limit (- max-width (count ellipsis))]
    (loop [i 0 w 0]
      (if (.find m)
        (let [code-begin  (.start m)
              code-length (count (.group m))
              str-length  (count (subs s i code-begin))
              new-width   (+ w str-length)
              overflow    (- new-width limit)]
          (if (pos? overflow)
            (str (subs s 0 (max 0 (- code-begin overflow)))
                 "\u001b[m"
                 ellipsis)
            (recur (long (+ code-begin code-length))
                   (long new-width))))
        (let [rest-str (subs s i)
              overflow (- (+ w (count rest-str)) limit)]
          (if (pos? overflow)
            (str (subs s 0 i)
                 (subs rest-str 0 (max 0 (- (count rest-str) overflow)))
                 ellipsis)
            s))))))

(defn ansi-str
  [any]
  (trim-ansi-str (puget/cprint-str any {:width Integer/MAX_VALUE
                                        :seq-limit 100})
                 80))

(defn ansi-str-multiline
  [any]
  (puget/cprint-str any {:seq-limit 100}))

(defmacro stack-trace
  []
  `(let [x#     (Exception.)
         lines# (str/split-lines (with-out-str (trace/print-stack-trace x#)))]
     (drop 1 lines#)))

(defn trim-form
  "Removes inspection forms injected by the reader tags"
  [form]
  (w/postwalk
   #(if (and (seq? %) (= 'inspector.core/? (first %)))
      (if (> (count %) 2)
        %
        (last %))
      %)
   form))

(defn wrap-ansi
  "Wraps the string with ANSI color code"
  [s col]
  (str "\u001b[" col "m" s "\u001b[m"))

(defn- trim-class-name
  [c]
  (symbol (str/replace-first (name c) #"^java\.lang\." "")))

(defn inspect-member
  [m]
  (let [type (some-> m :type trim-class-name)
        category (cond (:type m) :constant
                       (not (:return-type m)) :constructor
                       :else :method)]
    (assoc m
           :category   category
           :static?    (-> m :flags :static)
           :bridge?    (-> m :flags :bridge)
           :public?    (-> m :flags :public)
           :parameters (some->> m :parameter-types (mapv trim-class-name))
           :type       (or type (some-> m :return-type trim-class-name))
           :flags      (->> (disj (:flags m) :public) (map name) (str/join " ")))))
