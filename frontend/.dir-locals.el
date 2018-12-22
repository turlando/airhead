((nil . ((projectile-project-type . lein-test)
         (clojure-align-forms-automatically . t)
         (cider-lein-parameters . "with-profile +dev repl")
         (eval . (setq cider-cljs-lein-repl
                       "(do (require 'figwheel-sidecar.repl-api)
                            (figwheel-sidecar.repl-api/start-figwheel!)
                            (figwheel-sidecar.repl-api/cljs-repl))")))))
