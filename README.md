clj-inspector
=============

[![Build Status](https://travis-ci.org/junegunn/clj-inspector.svg?branch=master)](https://travis-ci.org/junegunn/clj-inspector)
[![codecov](https://codecov.io/gh/junegunn/clj-inspector/branch/master/graph/badge.svg)](https://codecov.io/gh/junegunn/clj-inspector)

*Inspector* helps debugging Clojure programs.

Installation
------------

To include *Inspector* in your development environment, add *Inspector* to
`:dependencies` and `:injections` of `user` profile in `~/.lein/profiles.clj`:

```clojure
{:user {:dependencies [[junegunn/inspector "0.2.0"]]
        :injections [(require 'inspector.core)]}}
```

Usage
-----

### Inspection with reader tags

`#i/?` inspects the next form and prints debugging information.

```clojure
(let [foo 1
      bar 2]
  #i/? (+ foo #i/? (* bar bar)))

;; ┌ (+ foo (* bar bar))
;; │  at user$eval40206.invokeStatic (form-init3537188508571310180.clj:1)
;; │ ┌ (* bar bar)
;; │ │  at user$eval40206$fn__40209.invoke (form-init3537188508571310180.clj:3)
;; │ │  result:  4
;; │ ├  locals:
;; │ │    bar: 2
;; │ │    foo: 1
;; │ └
;; │  result:  5
;; │  elapsed: 5ms
;; ├  locals:
;; │    bar: 2
;; │    foo: 1
;; └
```

By default, messages are printed to `*out*`, but you can change it with
`inspector.core/set-writer!`.

```clojure
(inspector.core/set-writer! System/err)
```

This is useful when you're connected to an nREPL server running on a terminal
window and you want the messages to be printed on that window.

### Inspection with macros

`inspector.core/?` macro can be used instead of `#i/?` tag.

```clojure
(require '[inspector.core :refer [?]])

(? (rand-int 100)
   (rand-int 200))

;; Prints:
;;   ┌ (rand-int 100)
;;   │  at user$eval40599.invoke (form-init7268911457864946014.clj:1)
;;   │  result:  19
;;   │  elapsed: 1ms
;;   └
;;   ┌ (rand-int 200)
;;   │  at user$eval40623.invoke (form-init7268911457864946014.clj:1)
;;   │  result:  83
;;   │  elapsed: 1ms
;;   └
;; Returns: 83
```

To retrieve the inspection result as a map, use `inspector.core/inspect`
macro.

```clojure
(require '[inspector.core :refer [inspect]])

(let [foo 1 bar 2] (inspect (+ foo bar)))
;; Returns:
;;   {:started <System/currentTimeMillis>,
;;    :locals {:foo 1, :bar 2}, :result 3, :form (+ foo bar), :elapsed 0}
```

You can use it to write derivative macros for capturing inspection reports.

```clojure
(def captures (atom []))

(defmacro ?>>
  "Macro that captures the inspection report of each form and returns the
  original evaluation result of the last form"
  [& forms]
  `(do ~@(for [form forms]
           `(let [report# (inspect ~form)]
              (swap! captures conj report#)
              (:result report#)))))
```

### Extra features in inspector.core

```clojure
(require '[inspector.core :refer [ls whereami env]])

;; Print vars in the current namespace
(ls)

;; Print vars in the given namespace
(ls *ns*)

;; Print the members of the object
(ls "java object")

;; Print stack trace
(whereami)

;; Capture lexical bindings as a map
(let [foo 1 bar 2] (env))
```

Inspiration
-----------

*Inspector* was heavily inspired by [spyscope][spyscope].

[spyscope]: https://github.com/dgrnbrg/spyscope

License
-------

The MIT License (MIT)

Copyright (c) 2017 Junegunn Choi
