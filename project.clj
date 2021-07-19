(defproject farstar "0.1.0"
  :description "Examples / Recipes using FoundationDB."
  :url "https://vedang.github.io/farstar/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [byte-streams "0.2.4"]
                 [com.taoensso/nippy "3.1.1"]
                 [clojure.java-time "0.3.2"]
                 [org.clojure/test.check "1.0.0"]
                 [com.gfredericks/test.chuck "0.2.10"]
                 [me.vedang/clj-fdb "0.3.0"]
                 [com.brunobonacci/mulog "0.7.1"]
                 [com.brunobonacci/mulog-zipkin "0.7.1"]
                 [com.brunobonacci/mulog-json "0.7.1"]]
  :plugins [[lein-codox "0.10.7"]]
  :codox {:source-uri "https://github.com/vedang/clj_fdb/blob/master/{filepath}#L{line}"
          :doc-files ["README.md"]
          :metadata {:doc/format :markdown}}
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
