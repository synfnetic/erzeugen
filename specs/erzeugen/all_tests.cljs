(ns erzeugen.all-tests
  (:require
    erzeugen.tests-to-run
    [doo.runner :refer-macros [doo-all-tests]]))

(doo-all-tests #".*-spec")
