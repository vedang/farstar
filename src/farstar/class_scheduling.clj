(ns farstar.class-scheduling
  "Implements the Class Scheduling example from FDB documentation, as
explained at :
https://apple.github.io/foundationdb/class-scheduling-java.html"
  (:require [byte-streams :as bs]
            [clojure.string :as cs]
            [com.brunobonacci.mulog :as mu]
            [me.vedang.clj-fdb.core :as fc]
            [me.vedang.clj-fdb.FDB :as cfdb]
            [me.vedang.clj-fdb.transaction :as ftr]
            [me.vedang.clj-fdb.tuple.tuple :as ftup]
            [me.vedang.clj-fdb.subspace.subspace :as fsub])
  (:import [com.apple.foundationdb Database Transaction TransactionContext]
           java.lang.IllegalArgumentException))

(def api-version 620)
(def fdb (cfdb/select-api-version api-version))
(def cs-subspace (fsub/create ["class_scheduling"]))

(load "util")

(defn available-classes
  "Returns a list of available classes. An available class is one with
  1 or more seats open for enrollment."
  [^TransactionContext db]
  (mu/trace ::available-classes
    []
    (reduce-kv (fn [m k v] (if (> v 0) (assoc m k v) m))
               ;; Sorting here just for aesthetic purposes, this is
               ;; not part of the orignal example.
               (sorted-map)
               (fc/get-range db cs-subspace ["class"]
                             {:valfn #(bs/convert % Integer)}))))

(defn- signup-student*
  "Internal function. Assumes all checks are cleared and we are inside
  a transaction."
  [^Transaction tr student-id class-id seats-left]
  (let [seats-left (int (dec seats-left))]
    (mu/log ::signup-student
            :level :debug
            :student-id student-id
            :class-id class-id
            :seats-left seats-left)
    (fc/set tr cs-subspace ["attends" class-id student-id] [])
    (fc/set tr cs-subspace ["attends" student-id class-id] [])
    (fc/set tr cs-subspace ["class" class-id] (bs/to-byte-array seats-left))
    class-id))

(defn signup-student
  "Signs up a student for a class. Constraints are as follows:

  - The student isn't already enrolled for this class.
  - The class should have seats available for enrollment.
  - The student can sign up for a maximum of 5 classes."
  [^TransactionContext db student-id class-id]
  (mu/trace ::signup-student
    [:student-id student-id
     :class-id class-id]
    (ftr/run db
      (fn [^Transaction tr]
        (if (fc/get tr cs-subspace ["attends" class-id student-id])
          (mu/log ::already-signed-up :student-id student-id :class-id class-id)
          (let [seats-left (fc/get tr cs-subspace ["class" class-id]
                                   {:valfn #(bs/convert % Integer)})
                previously-signed-up (count (fc/get-range tr cs-subspace ["attends" student-id]))]
            (cond
              (and seats-left (pos? seats-left) (< previously-signed-up 5))
              (signup-student* tr student-id class-id seats-left)

              (>= previously-signed-up 5)
              (throw (IllegalArgumentException.
                      (format "Hello %s! You've already signed up for the max number of allowed classes!"
                              student-id)))

              :else
              (throw (IllegalArgumentException.
                      (format "Sorry %s! No seats remaining in %s!"
                              student-id
                              class-id))))))))))

(defn- drop-student*
  "Internal function. Assumes all checks are cleared and we are inside
  a transaction."
  [^Transaction tr student-id class-id]
  (let [seats-left (fc/get tr cs-subspace ["class" class-id]
                           {:valfn #(bs/convert % Integer)})]
    (mu/log ::drop-student
            :level :debug
            :student-id student-id
            :class-id class-id
            :seats-left seats-left)
    (fc/clear tr cs-subspace ["attends" class-id student-id])
    (fc/clear tr cs-subspace ["attends" student-id class-id])
    (fc/set tr cs-subspace ["class" class-id]
            (bs/to-byte-array (int (inc seats-left))))
    class-id))


(defn drop-student
  "Drops a student from a class, if he is signed up for it."
  [^TransactionContext db student-id class-id]
  (mu/trace ::drop-student
    [:student-id student-id
     :class-id class-id]
    (ftr/run db
      (fn [^Transaction tr]
        (if (fc/get tr cs-subspace ["attends" class-id student-id])
          (drop-student* tr student-id class-id)
          (mu/log ::not-signed-up
                  :student-id student-id
                  :class-id class-id))))))


(defn switch-classes
  "Given a student-id and two class-ids, switch classes for the
  student! The contraints are as follows:

  - Don't drop the existing class unless the student has successfully
    signed up for the new class."
  [^TransactionContext db student-id old-class-id new-class-id]
  (mu/trace ::switch-classes
    [:student-id student-id
     :old-class-id old-class-id
     :new-class-id new-class-id]
    (ftr/run db
      (fn [^Transaction tr]
        (drop-student tr student-id old-class-id)
        (signup-student tr student-id new-class-id)))))

(defn add-class
  "Used to populate the database's class list. Adds a new class to the
  list of available classes and sets the number of available seats for
  the class."
  [^TransactionContext db class-id available-seats]
  (mu/trace ::add-class
    [:class-id class-id
     :seats-left available-seats]
    (fc/set db cs-subspace ["class" class-id]
            (bs/to-byte-array (int available-seats)))))

(defn init-db
  "Helper function to initialize the db with a bunch of classnames.
  Sets the number of available seats for each class to 10. Clears out
  the DB, so all existing information about classes and attendees is
  dropped."
  [^Database db classnames]
  (ftr/run db
    (fn [^Transaction tr]
      ;; Clear list of who attends which class
      (fc/clear-range tr cs-subspace ["attends"])
      ;; Clear list of classes
      (fc/clear-range tr cs-subspace ["class"])
      ;; Add list of classes as given to us
      (doseq [c classnames]
        (add-class tr c (int 10))))))


(defn reset-class
  "Helper function to remove all attendees from a class and reset it.
  If `available-seats` is provided, we use that number as the new
  value of `available-seats`. If not, we set the value to the number
  of attendees in the class + the number of seats still available in
  the class.

  *NOTE*: This is not part of the original example."
  ([^TransactionContext db class-id]
   (mu/trace ::reset-class
     [:class-id class-id]
     (ftr/run db
       (fn [^Transaction tr]
         (let [attendance-key ["attends" class-id]
               class-key ["class" class-id]
               att-count (count (fc/get-range tr cs-subspace attendance-key))
               seats-left (fc/get tr cs-subspace class-key
                                  {:valfn #(bs/convert % Integer)})]
           (reset-class db class-id (+ att-count seats-left)))))))
  ([^TransactionContext db class-id available-seats]
   (mu/trace ::reset-class
     [:available-seats available-seats
      :class-id class-id]
     (ftr/run db
       (fn [^Transaction tr]
         (doseq [s (keys (fc/get-range tr cs-subspace ["attends" class-id]
                                       {:keyfn (comp first (partial drop 2))}))]
           (drop-student tr s class-id))
         (add-class tr class-id (int available-seats)))))))


(defn reset-student
  "Drop the given student from all classes he has signed up for."
  [^TransactionContext db student-id]
  (mu/trace ::reset-student
    [:student-id student-id]
    (ftr/run db
      (fn [^Transaction tr]
        (doseq [c (keys (fc/get-range tr cs-subspace ["attends" student-id]
                                      {:keyfn (comp first (partial drop 2))}))]
          (drop-student tr student-id c))))))

;;; Testing

(defn- perform-random-action
  "Perform random actions. This function is going to respect the
  constraints from it's point of view. The magic is whether FDB can
  help us respect the constraints over a multi-threaded run."
  [^Database db student-id my-classes]
  (mu/trace ::perform-random-action
    [:student-id student-id
     :classes (seq my-classes)]
    (let [all-classes (map second (keys (available-classes db)))
          action (cond
                   ;; If I have no classes, I need to signup first.
                   (= 0 (count my-classes)) :add
                   ;; If I have 5 classes, I can't signup for more.
                   (= 5 (count my-classes)) (rand-nth [:drop :switch])
                   :else (rand-nth [:drop :switch :add]))]
      (try (case action
             :add (when-let [class-id (signup-student db
                                                      student-id
                                                      (rand-nth all-classes))]
                    (mu/log ::perform-random-action
                            :student-id student-id
                            :action :add
                            :class-id class-id)
                    (conj my-classes class-id))
             :drop (let [class-id (drop-student db
                                                student-id
                                                (rand-nth my-classes))]
                     (mu/log ::perform-random-action
                             :student-id student-id
                             :action :drop
                             :class-id class-id)
                     (remove #{class-id} my-classes))
             :switch (let [existing-class-id (rand-nth my-classes)
                           new-class-id (rand-nth (remove #{existing-class-id}
                                                          all-classes))]
                       (switch-classes db student-id existing-class-id new-class-id)
                       (mu/log ::perform-random-action
                               :student-id student-id
                               :action :switch
                               :existing-class-id existing-class-id
                               :new-class-id new-class-id)
                       (conj (remove #{existing-class-id} my-classes)
                             new-class-id)))
           (catch Exception e
             (mu/log ::perform-random-action
                     :status :failure
                     :exception e
                     :msg "INVESTIGATE THIS")
             my-classes)))))


(defn simulate-student
  "Simulates a student represented by `sid`. The student
  performs `ops-per-student` actions. Actions are any of:

  - `signup`: signup for a class (chosen at random)
  - `drop`: drop an existing class (chosen at random)
  - `switch`: switch from an existing class to a different
    class (chosen at random)."
  [sid ops-per-student]
  (mu/trace ::simulate-student
    [:student-id (str "Student: " sid)
     :ops ops-per-student]
    (let [student-id (str "Student: " sid)]
      (with-open [db (cfdb/open fdb)]
        (reduce (fn [my-classes _]
                  (perform-random-action db student-id my-classes))
                ;; Pass this student's current classes in.
                (keys (fc/get-range db cs-subspace ["attends" student-id]
                                    {:keyfn (comp first (partial drop 2))}))
                (range ops-per-student))))))


(defn run-sim
  "Runs the `simulate-student` function across multiple threads."
  [num-of-students ops-per-student]
  (mu/trace ::run-sim
    [:num-of-students num-of-students
     :ops-per-student ops-per-student]
    (let [futures (map (fn [i] (future (simulate-student i ops-per-student)))
                       (range num-of-students))]
      (mapv deref (shuffle futures)))))


(comment
  ;; Global context (app-name) is needed for the Zipkin / Jaeger
  ;; publisher for tracing.
  (mu/set-global-context!
   {:app-name "farstar", :version "0.1.0", :env "local"})

  ;; This will initialize our publishers. Mulog logs will show up only
  ;; after this is evaluated.
  (def stop-builtin-publishers
    (mu/start-publisher!
     {:type :multi
      :publishers
      [{:type :console
        :pretty? true
        :transform (fn [es] (remove (comp (partial = :debug) :level) es))}
       {:type :simple-file :filename "./logs/mulog_events.log"}]}))

  ;; If you want to stop publishers
  (stop-builtin-publishers)

  (def stop-jaeger-publisher
    (mu/start-publisher!
     {:type :zipkin

      ;; Zipkin endpoint (REQUIRED)
      :url  "http://localhost:9412/"

      ;; the maximum number of events which can be sent in a single
      ;; batch request to Zipkin
      :max-items     5000

      ;; Interval in milliseconds between publish requests.
      ;; μ/log will try to send the records to Zipkin
      ;; with the interval specified.
      :publish-delay 5000

      ;; a function to apply to the sequence of events before publishing.
      ;; This transformation function can be used to filter, transform,
      ;; anonymise events before they are published to a external system.
      ;; by defatult there is no transformation.  (since v0.1.8)
      :transform identity
      }))

  (stop-jaeger-publisher)

  ;; Create classes for fun and profit
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
      (init-db db classnames)))

  ;; List all the available classes
  (with-open [db (cfdb/open fdb)]
    (available-classes db))

  ;; Sign-up a student for a class
  (with-open [db (cfdb/open fdb)]
    (signup-student db "student-1" "101 alg 10:00"))

  ;; Switch classes for a student
  (with-open [db (cfdb/open fdb)]
    (switch-classes db "student-1" "101 alg 10:00" "101 alg 12:00"))

  ;; Drop a student from a class
  (with-open [db (cfdb/open fdb)]
    (drop-student db "student-1" "101 alg 12:00"))

  ;; Reset the state of the class
  (with-open [db (cfdb/open fdb)]
    (reset-class db "101 alg 12:00" 10))

  ;; Reset the state of a student
  (with-open [db (cfdb/open fdb)]
    (reset-student db "student-1"))

  ;; Simulate actions for a single student
  (simulate-student "1" 10)

  ;; Simulate actions for a bunch of students signing up for a bunch
  ;; of classes
  (run-sim 10 10)

  ;; Refer to the corresponding test namespace for tests against the sim
  )
