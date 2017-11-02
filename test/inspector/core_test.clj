(ns inspector.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [inspector.core :refer :all]
            [inspector.impl :as impl]))

(deftest test-ls
  (testing "Current namespace"
    (intern *ns* (with-meta 'a-var {:doc "A Var"}) nil)
    (is (re-find #"a-var \|.*A Var" (with-out-str (ls)))))

  (testing "Some namespace"
    (let [ns-table (with-out-str (ls (find-ns 'inspector.core)))]
      (is (.contains ^String ns-table "inspector.core/?"))
      (is (.contains ^String ns-table "inspector.core/ls"))))

  (testing "Java object members"
    (re-find #":method \|.* charAt \| *\[int\] \| *char"
             (with-out-str (ls "foo")))))

(defn whereami-caller [] (whereami))

(deftest test-whereami
  (is (re-find
       #"at.*whereami_caller"
       (-> (with-out-str (whereami-caller)) str/split-lines first))))

(deftest test-env
  (is (= {:foo 1 :bar 2} (let [foo 1 bar 2] (env)))))

(defn lines-of-output
  [s]
  (map #(str/replace % #" *$" "")
       (-> s
           (str/replace #"\u001b\[[0-9;]*m" "")
           (str/split-lines))))

(defn lines-of
  [s]
  (map #(str/replace % #"^ *" "") (str/split-lines s)))

(defn static-lines
  [lines]
  (for [line lines
        :when (every? #(not (.contains ^String line %))
                      ["elapsed:" " at "])]
    line))

(defn compare-lines
  [expected actual]
  (doseq [[expected actual] (map vector (static-lines expected) (static-lines actual))
          :when (every? #(not (.contains ^String expected %)) ["elapsed:" " at "])]
    (is (= expected actual))))

(deftest test-trim-ansi-str
  (doseq [[width expected] {0 ".."
                            1 ".."
                            2 ".."
                            3 "-.."
                            4 "--.."
                            5 "---.."
                            6 "----.."
                            7 "-----.."
                            8 "------.."
                            9 "-------.."}]
    (is (= expected (impl/trim-ansi-str (str/join (repeat 20 "-")) width))))
  (is (= "--\u001b[m.."
         (impl/trim-ansi-str
          (str (str/join (repeat 1000 "-")) "\u001b[34m")
          4))))

(testing "Basic usage of ?"
  (deftest test-?
    (let [output (lines-of-output
                  (with-out-str
                    (let [foo 1 bar 2]
                      (? #i/? [(? foo) #i/? (repeat 30 bar)]
                         bar))))
          expected
          (lines-of
           "
            ┌ [(? foo) (repeat 30 bar)]
            │  at inspector.core_test$eval28223$fn__28224.invoke (core_test.clj:53)
            │ ┌ [(? foo) (repeat 30 bar)]
            │ │  at inspector.core_test$eval28223$fn__28224$fn__28227.invoke (core_test.clj:53)
            │ │ ┌ foo
            │ │ │  at inspector.core_test$eval28223$fn__28224$fn__28227$fn__28230.invoke (core_test.clj:54)
            │ │ │  result:  1
            │ │ ├  locals:
            │ │ │    bar: 2
            │ │ │    foo: 1
            │ │ └
            │ │ ┌ (repeat 30 bar)
            │ │ │  at inspector.core_test$eval28223$fn__28224$fn__28227$fn__28253.invoke (core_test.clj:53)
            │ │ │  result:  (2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2)
            │ │ ├  locals:
            │ │ │    bar: 2
            │ │ │    foo: 1
            │ │ └
            │ ├  locals:
            │ │    bar: 2
            │ │    foo: 1
            │ └
            │  result:  [1 (2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2)]
            ├  locals:
            │    bar: 2
            │    foo: 1
            └
            ┌ bar
            │  at inspector.core_test$eval28223.invokeStatic (core_test.clj:53)
            │  result:  2
            ├  locals:
            │    bar: 2
            │    foo: 1
            └
            ")]
      (compare-lines expected output))))

(deftest test-inspect
  (let [{:keys [locals result form elapsed]}
        (let [foo 1 bar 2] (inspect (+ foo bar)))]
    (is (= 1 (:foo locals)))
    (is (= 2 (:bar locals)))
    (is (not (neg? elapsed)))
    (is (= '(+ foo bar) form))))

(deftest test-indented-multiline-output
  (is (= (lines-of "│ │ a: b
                   │ │ │  c
                   │ │ │  d")
         (lines-of
          (with-out-str
            (binding [impl/*indent* 2]
              (impl/output "a: " "b\nc\nd")))))))

(deftest test-trim-form
  (is (= '(inspector.core/? 1 (inspector.core/? 2 3) 4)
         (impl/trim-form
          '(inspector.core/? 1 (inspector.core/? 2 3) (inspector.core/? 4))))))

(deftest test-set-writer!
  (try
    (let [out (java.io.StringWriter.)]
      (set-writer! out)
      (let [foo 1 bar 2]
        (? [foo (? bar) #i/? (+ foo bar)]))
      (compare-lines (lines-of
                      "
                       ┌ [foo (? bar) (+ foo bar)]
                       │  at inspector.core_test$eval22981.invokeStatic (core_test.clj:107)
                       │ ┌ bar
                       │ │  at inspector.core_test$eval22981$fn__22984.invoke (core_test.clj:108)
                       │ │  result:  2
                       │ ├  locals:
                       │ │    bar: 2
                       │ │    foo: 1
                       │ └
                       │ ┌ (+ foo bar)
                       │ │  at inspector.core_test$eval22981$fn__23007.invoke (core_test.clj:107)
                       │ │  result:  3
                       │ ├  locals:
                       │ │    bar: 2
                       │ │    foo: 1
                       │ └
                       ├  locals:
                       │    bar: 2
                       │    foo: 1
                       └
                       ")
                     (lines-of-output (str out))))
    (finally (set-writer! nil))))
