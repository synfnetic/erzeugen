(ns erzeugen.erator
  #?(:cljs (:require-macros [erzeugen.erator :refer [defspawner]]))
  (:require [clojure.walk :as walk]
            [clojure.set :as set]))

;; for mocking
(defn genid [] (str (gensym)))

(defn with-extra [obj extra]
  (if (fn? obj)
    (with-meta
      (fn [& data]
        (mapv #(merge (apply obj %) extra) [data]))
      (meta obj))
    (fn ([] obj) ([data] (merge data extra)))))

(defn spawn-data
  ([seed-data] (spawn-data {} seed-data))
  ([ns->fn seed-data]
   (letfn [(make-datomic-id [n]
             (keyword "datomic.id" n))
           (make-om-tempid [n]
             (keyword  "om.tempid" n))
           (gen-step [generator data]
             ((if (fn? data) data identity) (apply generator (if (fn? data) (data) data))))
           (transform-keywords [kw]
             (if-not (keyword? kw) kw
               (let [default-ns->fn {"d.id" make-datomic-id
                                     "om.t" make-om-tempid
                                     "gen" #(case %
                                              "d.id" (make-datomic-id (genid))
                                              "om.t" (make-om-tempid  (genid)))}
                     kw-fn (get (merge ns->fn default-ns->fn)
                                (namespace kw)
                                (constantly kw))]
                 (kw-fn (name kw)))))]
     (let [seed-data (->> seed-data
                       (reduce
                         (fn [entities [gen gen-data]]
                           (->> (mapv #(gen-step gen %) gen-data)
                             (flatten)
                             (concat entities)))
                         [])
                       (walk/postwalk transform-keywords))
           entities (atom [])
           collect-entities (fn [x]
                              (if-not (and (map? x) (:db/id x)) x
                                (do (swap! entities conj x)
                                  (:db/id x))))]
       (walk/postwalk collect-entities seed-data)
       @entities)))
  ([ns->fn seed-data install-data]
   (->> seed-data
     (spawn-data ns->fn)
     (install-data))))

(defn spec->map-entry [{:keys [value attr xf] :as spec}]
  [attr (if-not xf value
          (if (::spawner (meta xf))
            (if (::ref-many (meta xf))
              (xf value)
              (apply xf value))
            (xf value)))])

(defn validate&parse! "validates and returns req + opt with values"
  [args kwargs req opt]
  (let [expected (set (mapv :param (concat req opt)))
        ?kwargs (set (filter (set kwargs) args))
        actual (set (if (empty? ?kwargs)
                      (take (count args) kwargs)
                      (take-nth 2 args)))]
    (assert (empty? (set/difference actual expected))
      (str "extra args: " (set/difference actual expected)))
    (when-let [extra-args (and (empty? ?kwargs)
                               (into [] (drop (count kwargs) args)))]
      (assert (empty? extra-args)
        (str "extra args: " extra-args)))
    (assert (empty? (set/difference (set (mapv :param req)) actual))
      (str "missing args: " (set/difference (set (mapv :param req)) actual) " args: " actual))
    (merge-with
      #(assoc %2 :value %1)
      (if (empty? ?kwargs)
        (zipmap kwargs args)
        (apply hash-map args))
      (into {} (map #(update % 1 first))
            (group-by :param (concat req opt))))))

(defn spawner* [args kwargs req opt dflts]
  (->> (validate&parse! args kwargs req opt)
    (into {} (comp (map second)
                   (filter :value)
                   (map spec->map-entry)))
    (merge dflts)))

(defn split-it [coll pred]
  (into (empty coll)
    (map #(into (empty coll) %))
    (partition-by (let [c (atom 0)]
                    #(do (when (pred %) (swap! c inc))
                         @c))
                  coll)))
(def ns-kw? (every-pred keyword? namespace))
(def kw? (every-pred keyword? (comp not namespace)))

(defn parse [opts]
  (let [has-kw? (first (filter kw? opts))]
    (cond-> {:attr (first opts)}
      has-kw?       (assoc :param has-kw?)
      (not has-kw?) (assoc :param (keyword (name (first opts))))
      (first (remove keyword? opts))
      (assoc :xf (first (remove keyword? opts))))))

#?(:clj (defmacro defspawner [name- {:keys [req opt or] :as spec}]
          (let [[req-seq opt-seq]
                (map #(mapv parse (split-it % ns-kw?))
                     [req opt])
                kwargs (mapv :param (concat req-seq opt-seq))]
            `(def ~name-
               (with-meta
                 (fn [~'& args#]
                   (spawner* args# ~kwargs
                     ~req-seq ~opt-seq ~or))
                 {::spawner ~(name name-)})))))

(defn compose [f & fs]
  (reduce (fn [F g]
            (with-meta
              (comp F g)
              (merge (meta F) (meta g))))
          f fs))

(defn ref-many [spawner]
  (with-meta
    (fn [args]
      (mapv (partial apply spawner) args))
    (merge {::ref-many true}
           (meta spawner))))
