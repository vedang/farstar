(ns farstar.class-scheduling-test
  (:require [byte-streams :as bs]
            [clojure.string :as cs]
            [clojure.test :refer [deftest is testing]]
            [farstar.class-scheduling :as fcs]
            [me.vedang.clj-fdb.core :as fc]
            [me.vedang.clj-fdb.FDB :as cfdb]
            [me.vedang.clj-fdb.subspace.subspace :as fsub]
            [me.vedang.clj-fdb.transaction :as ftr]
            [me.vedang.clj-fdb.tuple.tuple :as ftup]))

(def api-version 620)
(def fdb (cfdb/select-api-version api-version))
(def cs-subspace (fsub/create (ftup/from "class_scheduling")))


(defn verify-students
  "No student can attend more than 5 classes. Returns a list of bad entries"
  [db student-ids]
  (reduce (fn [bad-entries student-id]
            (let [classes (fc/get-range db
                                        cs-subspace
                                        (ftup/from "attends" student-id)
                                        (comp ftup/get-items (partial fsub/unpack cs-subspace))
                                        identity)]
              (if (> (count classes) 5)
                (conj bad-entries student-id)
                bad-entries)))
          []
          student-ids))


(defn verify-classes
  "Sum of seats left + attending students == 10 for every class"
  [db class-ids]
  (reduce (fn [bad-entries class-id]
            (ftr/read db
              (fn [tr]
                (let [attendance-key (ftup/from "attends" class-id)
                      class-key (ftup/from "class" class-id)
                      attendee-count (count (fc/get-range tr
                                                          cs-subspace
                                                          attendance-key
                                                          identity
                                                          identity))
                      seats-left (fc/get tr
                                         cs-subspace
                                         class-key
                                         #(bs/convert % Integer))]
                  (if (= 10 (+ attendee-count seats-left))
                    bad-entries
                    (conj bad-entries class-id))))))
          []
          class-ids))


(deftest class-scheduling-tests
  (testing "Initializing the DB creates classes"
    (let [class-levels ["intro" "for dummies" "remedial"
                        "101" "201" "301"
                        "mastery" "lab" "seminar"]
          class-types ["chem" "bio" "cs"
                       "geometry" "calc" "alg" "film"
                       "music" "art" "dance"]
          class-times ["2:00" "3:00" "4:00"
                       "5:00" "6:00" "7:00"
                       "8:00" "9:00" "10:00"
                       "11:00" "12:00" "13:00"
                       "14:00" "15:00" "16:00"
                       "17:00" "18:00" "19:00"]
          classnames (for [le class-levels
                           ty class-types
                           ti class-times]
                       (cs/join " " [le ty ti]))]
      (with-open [db (cfdb/open fdb)]
        (fcs/init-db db classnames)
        (is (= 1620 (count (fcs/available-classes db)))))))

  (testing "After simulating actions for a single student, invariants hold"
    (fcs/simulate-student "1" 10)
    (with-open [db (cfdb/open fdb)]
      (is (empty? (verify-students db ["Student: 1"])))
      (is (empty? (verify-classes db
                                  (keys (fc/get-range db
                                                      cs-subspace
                                                      (ftup/from "class")
                                                      (comp second ftup/get-items (partial fsub/unpack cs-subspace))
                                                      identity))))))))
