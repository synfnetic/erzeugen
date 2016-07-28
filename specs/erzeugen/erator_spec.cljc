(ns erzeugen.erator-spec
  (:require [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
             [specification behavior component assertions when-mocking]]
            [erzeugen.erator :as spawn :refer [spawn-data with-extra]]))

#?(:cljs (def AssertionError js/Error))

(defn update-keyword
  ([k name-fn]
   (update-keyword k identity name-fn))
  ([k ns-fn name-fn]
   (keyword (ns-fn (namespace k))
            (name-fn (name k)))))

(defn gen-A [id A] {:db/id id :A A})
(defn gen-B [id B] {:db/id id :B B})

(defn gen-C-with-A-B
  "think of it as a make question with en & es strings"
  [id C A B]
  (let [a-id (update-keyword id #(str % "-A"))
        b-id (update-keyword id #(str % "-B"))]
    [(gen-A a-id A)
     (gen-B b-id B)
     {:db/id id :C C :A a-id :B b-id}]))

(specification "spawn-data"
  (when-mocking
    (spawn/genid) =1x=> "G_1"
    (spawn/genid) =1x=> "G_2"
    (assertions
      "d.id as a namespace means :datomic.id"
      (spawn-data
        {gen-A [[:d.id/A0 "a0"]]})
      => [{:db/id :datomic.id/A0 :A "a0"}]

      "om.t as a namespace means :om.tempid"
      (spawn-data
        {gen-A [[:om.t/A0 "a0"]]})
      => [{:db/id :om.tempid/A0 :A "a0"}]

      ":gen/d.id => :datomic.id/(gensym)
      & :gen/om.t =>  :om.tempid/(gensym)"
      (spawn-data
        {gen-A [[:gen/d.id :gen/om.t]]})
      => [{:db/id :datomic.id/G_1 :A :om.tempid/G_2}]

      "a generator can return a list of entities"
      (spawn-data
        {gen-C-with-A-B
         [[:d.id/C0 "C/val" "a/val" "b/val"]]})
      => [{:db/id :datomic.id/C0-A :A "a/val"}
          {:db/id :datomic.id/C0-B :B "b/val"}
          {:db/id :datomic.id/C0
           :C "C/val"
           :A :datomic.id/C0-A
           :B :datomic.id/C0-B}]

      "nested entities will get hoisted up"
      (spawn-data
        {gen-A [[:d.id/A0 {:db/id :d.id/A1 :A "A1"}]]})
      => [{:db/id :datomic.id/A1 :A "A1"}
          {:db/id :datomic.id/A0 :A :datomic.id/A1}]

      "can pre-process based on keyword namespaces"
      (let [lookup-user {"u0" "transformed-u0"}
            to-fake-uuid #(keyword "fake-uuid" %)]
        (spawn-data
          {"USER" (fn [kw-name] (->> kw-name lookup-user to-fake-uuid))}
          {gen-A
           [[:d.id/A0 :USER/u0]]}))
      => [{:db/id :datomic.id/A0 :A :fake-uuid/transformed-u0}]

      "can add (otherwise) repeated data to all items under a generator"
      (spawn-data
        {(with-extra gen-A {:some/repeated :d.id/data})
         [[:d.id/A0 "a0"]
          [:d.id/A1 "a1"]
          [:d.id/A2 "a2"]]})
      => [{:db/id :datomic.id/A0 :A "a0" :some/repeated :datomic.id/data}
          {:db/id :datomic.id/A1 :A "a1" :some/repeated :datomic.id/data}
          {:db/id :datomic.id/A2 :A "a2" :some/repeated :datomic.id/data}]

      "can add one-off assoc'ed on data to an item under a generator"
      (spawn-data
        {gen-A [[:d.id/A0 "a0"]
                (with-extra [:d.id/A1 "a1"] {:foo :bar})]})
      => [{:db/id :datomic.id/A0 :A "a0"}
          {:db/id :datomic.id/A1 :A "a1" :foo :bar}])))

(def evil-scale {0 "lame" 1 "meh" 2 "ok" 3 "good" 4 "great" 5 "awesome"})

(spawn/defspawner demon
  {:req [:db/id
         :demon/title :name]
   :opt [:demon.evil/level evil-scale
         :demon/type str]})

(spawn/defspawner angel
  {:req [:db/id]
   :opt [:angelic/aura]
   :or  {:angelic/aura 7}})

(specification "defspawn"
  (assertions
    "takes kw args"
    (demon :id 666 :name "lucy" :type 'fallen-angel :level 5)
    => {:db/id 666
        :demon/title "lucy"
        :demon.evil/level "awesome"
        :demon/type "fallen-angel"}
    "doesnt use optional arguments if they arent present"
    (demon :id 666 :name "lucy")
    => {:db/id 666
        :demon/title "lucy"}
    "asserts you provide all :req(uired) params"
    (demon :id 123)
    =throws=> (AssertionError #"missing")
    "asserts you pass it no extra arguments"
    (demon :id 123 :name "luci" :adsf :oops)
    =throws=> (AssertionError #"extra args")
    "can provide fallback to optional parameters"
    (angel :id 42)
    => {:db/id 42 :angelic/aura 7}
    (angel :id 42 :aura 3)
    => {:db/id 42 :angelic/aura 3})
  (component "can also take positional arguments"
    (assertions
      (demon 666 "lucy" 5 'angel)
      => {:db/id 666
          :demon/title "lucy"
          :demon.evil/level "awesome"
          :demon/type "angel"}
      (demon 666 "lucy")
      => {:db/id 666
          :demon/title "lucy"}
      "asserts you provide all required params"
      (demon 42)
      =throws=> (AssertionError #"missing")
      "asserts you pass it no extra arguments"
      (demon 123 "tyrael" 123 513 :BAD :EXTRA)
      =throws=> (AssertionError #"extra args"))))

(spawn/defspawner weapon
  {:req [:db/id
         :weapon/type]})

(spawn/defspawner nephalem
  {:req [:db/id]
   :opt [:parent/angel angel
         :parent/demon demon
         :nephalem/weapons (spawn/ref-many weapon)]})

(specification "nested spawning"
  (assertions
    (nephalem :d.id/nephalem [:d.id/angel 3] [:d.id/demon "demon"])
    => {:db/id :d.id/nephalem
        :parent/angel {:db/id :d.id/angel :angelic/aura 3}
        :parent/demon {:db/id :d.id/demon :demon/title "demon"}}
    "ref-many"
    (nephalem :id :d.id/nephalem :weapons [[:d.id/sword 7]])
    => {:db/id :d.id/nephalem
        :nephalem/weapons [{:db/id :d.id/sword :weapon/type 7}]}))
