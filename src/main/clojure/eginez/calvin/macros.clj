
(ns eginez.calvin.macros)

(defmacro try-true
 [& exprs]
 `(try
    (do
      ~@exprs
      true)
    (catch ~'js/Error ~'e false)))

(defmacro if-let*
  ([bindings then]
   `(if-let* ~bindings ~then nil))
  ([bindings then else]
   (if (seq bindings)
     `(if-let [~(first bindings) ~(second bindings)]
        (if-let* ~(drop 2 bindings) ~then ~else)
        ~(if-not (second bindings) else))
     then)))
