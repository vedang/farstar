(ns farstar.time-series
  "Implements the Time Series example from FDB documentation, as
explained at : https://apple.github.io/foundationdb/time-series.html"
  (:require [byte-streams :as bs]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [me.vedang.clj-fdb.core :as fc]
            [me.vedang.clj-fdb.directory.directory :as fdir]
            [me.vedang.clj-fdb.FDB :as cfdb]
            [me.vedang.clj-fdb.subspace.subspace :as fsub]
            [me.vedang.clj-fdb.transaction :as ftr]
            [me.vedang.clj-fdb.tuple.tuple :as ftup])
  (:import com.apple.foundationdb.MutationType
           java.util.UUID))

;;; We are going to build a small simulation of a time-series database
;;; that is counting the number of visits to a webpage. This database
;;; keeps track of [website, webpage, browser] for each hit. It can
;;; count total hits across all websites as well as total hits across
;;; a single website.


(def api-version 620)
(def fdb (cfdb/select-api-version api-version))
(with-open [db (cfdb/open fdb)]
  (def ts-dir (fdir/create-or-open! db ["time_series"]))
  (def ts-website-counter-dir
    (fdir/create-or-open! db ["time_series" "website_counter"])))


(def valid-website-id? (partial re-matches #"^W[0-9]{1}$"))
(def valid-page-id? (partial re-matches #"^P[0-9]{2}$"))
(def valid-browser-id? #(try (UUID/fromString %)
                             (catch IllegalArgumentException _ nil)))

(s/def ::website-id (s/with-gen valid-website-id?
                      (fn [] (gen/fmap #(str "W" %) (gen/choose 0 9)))))
(s/def ::page-id (s/with-gen valid-page-id?
                   (fn [] (gen/fmap #(str "P" %) (gen/choose 10 99)))))
(s/def ::browser-id (s/with-gen valid-browser-id?
                      (fn [] (gen/fmap str (gen/uuid)))))
(s/def ::year #{2020 2021})
(s/def ::month (set (range 1 13)))
(s/def ::day (set (range 1 32))) ;; Yes, I am not matching month and day here,
                                 ;; but this is an example. This
                                 ;; leeway should be okay.
(s/def ::hour (set (range 24)))
(s/def ::minute (set (range 60)))
(s/def ::second (set (range 60)))
(s/def ::date (s/tuple ::year ::month ::day ::hour ::minute ::second))
(s/def ::visitor (s/tuple ::website-id ::page-id ::browser-id))


(defn track-visitor
  "Given `date` and `visitor` tuples, store the visit against the date.
  Also, update the total number of views against the website in the
  visitor tuple."
  [db date visitor]
  (let [website-visit-counter (into (vector)
                                    (concat (take 1 visitor) (take 3 date)))]
   (ftr/run db
     (fn [tr]
       (fc/set tr ts-dir date visitor)
       (fc/mutate! tr
                   :add
                   ts-website-counter-dir
                   website-visit-counter
                   (bs/to-byte-array (int 1)))))))


(comment
  (gen/sample (s/gen ::date))
  (gen/sample (s/gen ::visitor))

  ;; Insert randomly generated views
  (gen/generate (s/gen ::date))
  ;; [2020 1 1 19 23 19]
  (gen/generate (s/gen ::visitor))
  ;; ["W7" "P33" "9fc5b6b7-654f-4ba4-9e7d-cc91559efe69"]
  (with-open [db (cfdb/open fdb)]
    (track-visitor db
                   [2021 1 1 19 23 19]
                   ["W7" "P33" "9fc5b6b7-654f-4ba4-9e7d-cc91559efe69"]))
  ;; => nil
  (with-open [db (cfdb/open fdb)]
    (fc/get-range db ts-dir))
  ;; {[2020 1 1 19 23 19] ["W7" "P33" "9fc5b6b7-654f-4ba4-9e7d-cc91559efe69"]}
  (with-open [db (cfdb/open fdb)]
    (fc/get-range db ts-website-counter-dir {:valfn #(bs/convert % Integer)}))
  ;; {["W7" 2020 1 1] 1}
  (with-open [db (cfdb/open fdb)]
    (track-visitor db
                   [2020 1 1 20 20 20]
                   ["W7" "P10" "9fc5b6b7-654f-4ba4-9e7d-cc91559efe69"]))
  ;; nil
  (with-open [db (cfdb/open fdb)]
    (fc/get-range db ts-dir))
  ;; {[2020 1 1 19 23 19] ["W7" "P33" "9fc5b6b7-654f-4ba4-9e7d-cc91559efe69"],
  ;;  [2020 1 1 20 20 20] ["W7" "P10" "9fc5b6b7-654f-4ba4-9e7d-cc91559efe69"]}
  (with-open [db (cfdb/open fdb)]
    (fc/get-range db ts-website-counter-dir {:valfn #(bs/convert % Integer)}))
  ;; {["W7" 2020 1 1] 2}

  ;; Next steps:
  ;; 1. generate lots of views,
  ;; 2. add tests
  )
