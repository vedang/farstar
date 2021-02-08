(defproject farstar "0.1.0"
  :description "Examples / Recipes using FoundationDB."
  :url "https://vedang.github.io/farstar/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.2"]
                 [me.vedang/clj-fdb "0.1.1"]
                 [com.brunobonacci/mulog "0.6.4"]
                 [com.brunobonacci/mulog-zipkin "0.6.4"]
                 [com.brunobonacci/mulog-json "0.6.4"]]
  :plugins [[lein-codox "0.10.7"]]
  :codox {:source-uri "https://github.com/vedang/clj_fdb/blob/master/{filepath}#L{line}"
          :doc-files ["README.md"]
          :metadata {:doc/format :markdown}}
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
