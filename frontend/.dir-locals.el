((nil . ((whitespace-style . (face
                              tabs tab-mark indentation::tab
                              spaces space-mark indentation::space
                              newline newline-mark
                              trailing))

         (clojure-align-forms-automatically . t)
         (cider-lein-parameters . "with-profile +dev repl")

         (eval . (setq cider-cljs-lein-repl
                       "(do (require 'figwheel-sidecar.repl-api)
                            (figwheel-sidecar.repl-api/start-figwheel!)
                            (figwheel-sidecar.repl-api/cljs-repl))"))))
