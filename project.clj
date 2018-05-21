(defproject farstar "0.1.0"
  :description "Examples / Recipes using FoundationDB."
  :url "https://vedang.github.io/farstar/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-fdb "0.1.0"]]
  :main ^:skip-aot farstar.core
  :codox {:source-uri "https://github.com/vedang/clj_fdb/blob/master/{filepath}#L{line}"
          :doc-files ["README.md"]
          :metadata {:doc/format :markdown}}
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
